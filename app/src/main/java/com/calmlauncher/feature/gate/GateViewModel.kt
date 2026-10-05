package com.calmlauncher.feature.gate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.LaunchLogEntity
import com.calmlauncher.data.rules.PassStore
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.tools.LaunchLogRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.domain.focus.BlockReason
import com.calmlauncher.domain.focus.LaunchDecision
import com.calmlauncher.domain.focus.PassKind
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.service.LaunchController
import com.calmlauncher.service.Notifier
import com.calmlauncher.service.Scheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

sealed interface GateState {
    data object Loading : GateState
    data class Pause(val seconds: Int, val askReason: Boolean) : GateState
    data class Block(val reason: BlockReason, val strictFocus: Boolean) : GateState
    data object Done : GateState
}

@HiltViewModel
class GateViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val policy: PolicyEvaluator,
    private val apps: AppsRepository,
    private val passes: PassStore,
    private val log: LaunchLogRepository,
    private val rules: RulesRepository,
    private val settingsRepository: SettingsRepository,
    private val usage: UsageRepository,
    private val launchController: LaunchController,
    private val scheduler: Scheduler,
    private val notifier: Notifier,
) : ViewModel() {

    val packageName: String = savedState.get<String>(GateActivity.EXTRA_PACKAGE).orEmpty()
    private val keyId: String? = savedState.get<String>(GateActivity.EXTRA_KEY)
    val external: Boolean = savedState.get<Boolean>(GateActivity.EXTRA_EXTERNAL) ?: false

    private val _state = MutableStateFlow<GateState>(GateState.Loading)
    val state: StateFlow<GateState> = _state.asStateFlow()

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings

    /** One-time challenge text for strict exits, kept across rotation. */
    val challenge: String = savedState.get<String>(KEY_CHALLENGE) ?: newChallenge().also { savedState[KEY_CHALLENGE] = it }

    val appLabel: String get() = apps.labelFor(packageName)

    init {
        notifier.cancelGate()
        viewModelScope.launch { evaluate() }
    }

    private suspend fun evaluate() {
        usage.invalidate()
        when (val d = policy.decide(packageName)) {
            is LaunchDecision.Allow -> proceed()
            is LaunchDecision.Pause -> _state.value = GateState.Pause(d.seconds, d.askReason)
            is LaunchDecision.Block -> _state.value = GateState.Block(d.reason, policy.focusState().strict)
        }
    }

    /** User chose to continue after the pause. */
    fun continueAfterPause(reason: String?, note: String?) {
        viewModelScope.launch {
            log.log(packageName, LaunchLogEntity.PAUSE_CONTINUED, reason, note)
            passes.grant(packageName, PassKind.PAUSE, settings.value.passMinutes.coerceIn(1, 60) * MINUTE)
            proceed()
        }
    }

    fun cancelPause(reason: String?, note: String?) {
        viewModelScope.launch {
            log.log(packageName, LaunchLogEntity.PAUSE_CANCELLED, reason, note)
            close()
        }
    }

    fun closeBlocked() {
        viewModelScope.launch {
            log.log(packageName, LaunchLogEntity.BLOCK_CLOSED)
            close()
        }
    }

    fun extendLimit(minutes: Int, isGroup: Boolean) {
        viewModelScope.launch {
            rules.extend(policy.limitTargetFor(packageName, isGroup), minutes)
            log.log(packageName, LaunchLogEntity.BLOCK_EXTENDED, reason = "+$minutes")
            evaluate()
        }
    }

    fun unlimitedToday(isGroup: Boolean) {
        viewModelScope.launch {
            rules.unlimitedToday(policy.limitTargetFor(packageName, isGroup))
            log.log(packageName, LaunchLogEntity.BLOCK_EXTENDED, reason = "day")
            evaluate()
        }
    }

    /** Deliberate override of a schedule/always block, valid for a few minutes. */
    fun overrideBlock() {
        viewModelScope.launch {
            log.log(packageName, LaunchLogEntity.BLOCK_OVERRIDDEN)
            passes.grant(packageName, PassKind.OVERRIDE, OVERRIDE_MINUTES * MINUTE)
            proceed()
        }
    }

    fun endFocus() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(focusManualActive = false, focusManualUntil = 0L) }
            // Schedules can't be "ended", so allow this app for the rest of the session.
            if (policy.isFocusActive()) passes.grant(packageName, PassKind.OVERRIDE, OVERRIDE_MINUTES * MINUTE)
            scheduler.rescheduleAll()
            evaluate()
        }
    }

    private fun proceed() {
        if (!external) {
            val key = keyId?.let { AppKey.parse(it) } ?: apps.findByPackage(packageName)?.key
            if (key != null) launchController.launchNow(key)
        }
        _state.value = GateState.Done
    }

    private fun close() {
        if (external) launchController.goHome()
        _state.value = GateState.Done
    }

    private fun newChallenge(): String {
        val words = listOf("quiet", "river", "slow", "morning", "breathe", "stone", "light", "garden", "patient", "cloud", "steady", "walk")
        return (1..4).joinToString(" ") { words[Random.nextInt(words.size)] }
    }

    companion object {
        private const val MINUTE = 60_000L
        const val OVERRIDE_MINUTES = 5L
        private const val KEY_CHALLENGE = "challenge"
    }
}
