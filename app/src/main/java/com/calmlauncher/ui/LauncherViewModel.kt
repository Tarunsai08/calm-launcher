package com.calmlauncher.ui

import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.apps.IconCache
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.model.AppKey
import com.calmlauncher.domain.model.LauncherAction
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.AppLauncher
import com.calmlauncher.service.EnforcementController
import com.calmlauncher.service.LaunchController
import com.calmlauncher.service.LockResult
import com.calmlauncher.service.Scheduler
import com.calmlauncher.service.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-only effects the activity-level view model asks the Compose tree to perform. */
sealed interface UiEffect {
    data object OpenDrawer : UiEffect
    data object OpenSearch : UiEffect
    data class Navigate(val route: String) : UiEffect
    data class Message(val textRes: Int) : UiEffect
    data object LockNeedsSetup : UiEffect
}

/**
 * Activity-scoped view model shared by the whole launcher UI. Owns action execution so that
 * gestures, quick-launch slots, clock/date taps and accessibility custom actions all behave
 * identically.
 */
@HiltViewModel
class LauncherViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
    val apps: AppsRepository,
    val icons: IconCache,
    val launcher: AppLauncher,
    private val launchController: LaunchController,
    private val systemActions: SystemActions,
    private val policy: PolicyEvaluator,
    private val rules: RulesRepository,
    private val scheduler: Scheduler,
    private val enforcement: EnforcementController,
) : ViewModel() {

    val settings: StateFlow<LauncherSettings> = settingsRepository.settings
    val appList: StateFlow<List<LauncherApp>> = apps.apps

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects.asSharedFlow()

    fun launchApp(key: AppKey, bounds: Rect? = null) {
        viewModelScope.launch { launchController.launch(key, bounds) }
    }

    fun perform(action: LauncherAction) {
        when (action) {
            LauncherAction.None -> Unit
            LauncherAction.OpenDrawer -> _effects.tryEmit(UiEffect.OpenDrawer)
            LauncherAction.OpenSearch -> _effects.tryEmit(UiEffect.OpenSearch)
            LauncherAction.ExpandNotifications -> if (!systemActions.expandNotifications()) _effects.tryEmit(UiEffect.OpenSearch)
            LauncherAction.LockScreen -> lock()
            LauncherAction.OpenLauncherSettings -> _effects.tryEmit(UiEffect.Navigate(Routes.SETTINGS))
            LauncherAction.ToggleFocus -> toggleFocus()
            LauncherAction.OpenNotes -> _effects.tryEmit(UiEffect.Navigate(Routes.NOTES))
            LauncherAction.OpenTodos -> _effects.tryEmit(UiEffect.Navigate(Routes.TODOS))
            LauncherAction.OpenInsights -> _effects.tryEmit(UiEffect.Navigate(Routes.INSIGHTS))
            LauncherAction.OpenDigest -> _effects.tryEmit(UiEffect.Navigate(Routes.DIGEST))
            LauncherAction.DefaultClock -> launcher.openClock()
            LauncherAction.DefaultCalendar -> launcher.openCalendar()
            LauncherAction.DefaultDialer -> launcher.openDialer()
            LauncherAction.DefaultCamera -> launcher.openCamera()
            is LauncherAction.LaunchApp -> launchApp(action.key)
        }
    }

    private fun lock() {
        val method = settings.value.lockMethod
        if (method == LockMethod.NONE) {
            _effects.tryEmit(UiEffect.LockNeedsSetup)
            return
        }
        if (systemActions.lockScreen() == LockResult.NEEDS_SETUP) _effects.tryEmit(UiEffect.LockNeedsSetup)
    }

    /** Toggle from home. Strict mode and schedules route to the focus screen (exit challenge). */
    private fun toggleFocus() {
        val s = settings.value
        val active = policy.isFocusActive()
        if (active && (s.focusStrict || !s.focusManualActive)) {
            _effects.tryEmit(UiEffect.Navigate(Routes.FOCUS))
            return
        }
        viewModelScope.launch {
            settingsRepository.update { it.copy(focusManualActive = !active, focusManualUntil = 0L) }
            scheduler.rescheduleAll()
        }
    }

    fun isFocusActive(): Boolean = policy.isFocusActive()

    fun blockedByFocus(packageName: String): Boolean = policy.blockedByFocus(packageName)

    fun focusRulesVersion() = rules.snapshot

    fun onHomeVisible() {
        enforcement.resetForeground()
        scheduler.refreshMonitor()
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setFavorite(key: AppKey, favorite: Boolean) = viewModelScope.launch { apps.setFavorite(key, favorite) }
    fun moveFavorite(key: AppKey, delta: Int) = viewModelScope.launch { apps.moveFavorite(key, delta) }
    fun setHidden(key: AppKey, hidden: Boolean) = viewModelScope.launch { apps.setHidden(key, hidden) }
    fun setAlias(key: AppKey, alias: String?) = viewModelScope.launch { apps.setAlias(key, alias) }
    fun setCategory(key: AppKey, categoryId: Long?) = viewModelScope.launch { apps.setCategory(key, categoryId) }

    fun emit(effect: UiEffect) {
        _effects.tryEmit(effect)
    }
}
