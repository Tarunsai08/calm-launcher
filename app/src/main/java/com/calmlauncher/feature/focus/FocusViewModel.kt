package com.calmlauncher.feature.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.db.CategoryEntity
import com.calmlauncher.data.db.TimeWindowEntity
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.rules.RulesSnapshot
import com.calmlauncher.data.settings.FocusHideMode
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.focus.FocusRule
import com.calmlauncher.domain.focus.RuleTarget
import com.calmlauncher.domain.focus.TimeWindow
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.Scheduler
import com.calmlauncher.service.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val rules: RulesRepository,
    private val policy: PolicyEvaluator,
    private val scheduler: Scheduler,
    private val systemActions: SystemActions,
    apps: AppsRepository,
) : ViewModel() {
    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
    val snapshot: StateFlow<RulesSnapshot> = rules.snapshot
    val apps: StateFlow<List<LauncherApp>> = apps.apps
    val categories: StateFlow<List<CategoryEntity>> = rules.categories

    fun isActive(): Boolean = policy.isFocusActive()
    fun activeUntil(): ZonedDateTime? = policy.focusState().activeUntil(ZonedDateTime.now())
    fun rulesPaused(): Boolean = policy.rulesPaused()
    fun enforcementReady(): Boolean = systemActions.isServiceConnected()

    private fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            scheduler.rescheduleAll()
        }
    }

    fun startFocus(minutes: Int?) = update {
        it.copy(
            focusManualActive = true,
            focusManualUntil = minutes?.let { m -> System.currentTimeMillis() + m * 60_000L } ?: 0L,
        )
    }

    fun stopFocus() = update { it.copy(focusManualActive = false, focusManualUntil = 0L) }

    fun setStrict(value: Boolean) = update { it.copy(focusStrict = value) }

    fun setHideMode(mode: FocusHideMode) = update { it.copy(focusHideMode = mode) }

    fun toggleAllowed(packageName: String) = update {
        val set = it.focusAllowlist.toMutableSet()
        if (!set.add(packageName)) set.remove(packageName)
        it.copy(focusAllowlist = set)
    }

    fun setPauseDefaults(seconds: Int) = update { it.copy(defaultPauseSeconds = seconds) }

    fun saveSchedule(id: Long, name: String, window: TimeWindow, enabled: Boolean) {
        viewModelScope.launch {
            rules.saveWindow(
                TimeWindowEntity(
                    id = id,
                    owner = TimeWindowEntity.OWNER_FOCUS,
                    name = name,
                    daysMask = window.daysMask,
                    startMinute = window.startMinute,
                    endMinute = window.endMinute,
                    enabled = enabled,
                ),
            )
            scheduler.rescheduleAll()
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            rules.deleteWindow(id)
            scheduler.rescheduleAll()
        }
    }

    /** Emergency escape hatch: suspends every rule and focus mode for an hour. */
    fun pauseEverything(minutes: Int = 60) = update {
        it.copy(
            rulesPausedUntil = System.currentTimeMillis() + minutes * 60_000L,
            focusManualActive = false,
            focusManualUntil = 0L,
        )
    }

    fun resumeRules() = update { it.copy(rulesPausedUntil = 0L) }

    fun setEnforcement(enabled: Boolean) = update { it.copy(enforcementEnabled = enabled) }

    fun setFallbackMonitor(enabled: Boolean) = update { it.copy(fallbackMonitorEnabled = enabled) }

    /** Target keys of apps/groups that have any rule, for the overview list. */
    fun ruleTargets(snapshot: RulesSnapshot): List<Pair<RuleTarget, FocusRule>> =
        snapshot.rules.values.filter { !it.isEmpty }.map { it.target to it }
}
