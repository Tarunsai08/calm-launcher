package com.calmlauncher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Opt-in accessibility service with two narrow jobs:
 * 1. Perform the "lock screen" / "open notifications" global actions for home gestures.
 * 2. Notice which *app package* comes to the foreground so focus rules also apply when an
 *    app is opened from outside the launcher.
 *
 * It listens to window-state changes only, cannot retrieve window content, and never reads
 * text on screen. See res/xml/accessibility_service_config.xml and docs/PRIVACY.md.
 */
@AndroidEntryPoint
class CalmAccessibilityService : AccessibilityService() {

    @Inject lateinit var enforcement: EnforcementController

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        enforcement.onForeground(pkg, fromAccessibility = true)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: CalmAccessibilityService? = null
            private set
    }
}
