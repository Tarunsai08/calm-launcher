package com.calmlauncher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.domain.navigation.NavGesture
import com.calmlauncher.service.navigation.AppSwitcher
import com.calmlauncher.service.navigation.GestureNavOverlay
import com.calmlauncher.service.navigation.NavBarController
import com.calmlauncher.service.navigation.NavOverlayConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Opt-in accessibility service ("focus helper") with three narrow jobs:
 * 1. Perform the "lock screen" / "open notifications" global actions for home gestures.
 * 2. Notice which *app package* comes to the foreground so focus rules also apply when an
 *    app is opened from outside the launcher.
 * 3. Optional navigation gestures: thin edge strips that turn swipes into back / home /
 *    recent apps, for phones (like Xiaomi) that switch to buttons with a third-party launcher.
 *
 * It listens to window-state changes only, cannot retrieve window content, and never reads
 * text on screen. See res/xml/accessibility_service_config.xml and docs/PRIVACY.md.
 */
@AndroidEntryPoint
class CalmAccessibilityService : AccessibilityService() {

    @Inject lateinit var enforcement: EnforcementController
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var navBar: NavBarController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: GestureNavOverlay? = null
    private var switcher: AppSwitcher? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = GestureNavOverlay(this, ::onNavGesture)
        switcher = AppSwitcher(this)
        scope.launch {
            combine(settings.settings, navBar.trial) { s, _ -> s }.collect { applyNavigation(it) }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        switcher?.onForeground(pkg)
        enforcement.onForeground(pkg, fromAccessibility = true)
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.relayout()
    }

    /** Shows/hides the edge strips and the system buttons to match the user's settings. */
    private fun applyNavigation(s: LauncherSettings) {
        val enabled = s.navGesturesEnabled
        val bottomWanted = enabled && s.navBottomGestures
        when {
            bottomWanted && s.navHideButtons -> if (!navBar.hiddenByUs()) navBar.hideButtons()
            bottomWanted && navBar.isTrialRunning() -> Unit
            else -> navBar.restoreButtons()
        }
        // Our bottom strip only replaces the system's gestures where the buttons are hidden by
        // Xiaomi's setting (otherwise it would sit on top of the buttons or native gestures).
        val bottom = bottomWanted && navBar.isXiaomi() && navBar.buttonsHidden()
        val config = if (!enabled) {
            null
        } else {
            NavOverlayConfig(
                sides = s.navBackFromSides,
                bottom = bottom,
                quickSwitch = s.navQuickSwitch,
                edgeWidthDp = s.navEdgeWidthDp,
                bottomHeightDp = s.navBottomHeightDp,
                haptics = s.navHaptics,
                showHandle = s.navShowHandle,
            )
        }
        overlay?.apply(config)
    }

    private fun onNavGesture(gesture: NavGesture) {
        when (gesture) {
            NavGesture.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            NavGesture.HOME -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
                confirmHideTrial()
            }
            NavGesture.RECENTS -> {
                performGlobalAction(GLOBAL_ACTION_RECENTS)
                confirmHideTrial()
            }
            NavGesture.SWITCH_PREVIOUS -> switcher?.switch(previous = true)
            NavGesture.SWITCH_NEXT -> switcher?.switch(previous = false)
            NavGesture.NONE, NavGesture.TAP, NavGesture.LONG_PRESS -> Unit
        }
    }

    /** A working swipe from the bottom proves the gestures work, so keep the buttons hidden. */
    private fun confirmHideTrial() {
        if (!navBar.isTrialRunning()) return
        scope.launch {
            // Save first, so the settings collector never sees "confirmed but not wanted".
            settings.update { it.copy(navHideButtons = true) }
            navBar.confirmTrial()
        }
    }

    private fun shutDownNavigation() {
        overlay?.detach()
        overlay = null
        // Never leave the phone without any way to navigate.
        navBar.restoreButtons()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        shutDownNavigation()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        shutDownNavigation()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: CalmAccessibilityService? = null
            private set
    }
}
