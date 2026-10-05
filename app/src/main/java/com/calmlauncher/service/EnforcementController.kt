package com.calmlauncher.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.calmlauncher.data.di.ApplicationScope
import com.calmlauncher.data.rules.PolicyEvaluator
import com.calmlauncher.data.rules.RulesRepository
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.domain.focus.LaunchDecision
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies focus rules to apps opened from *outside* the launcher (notifications, recents,
 * other launchers, links). Fed by the opt-in accessibility service, or by the usage-stats
 * fallback monitor. Only the foreground package name is ever looked at.
 */
@Singleton
class EnforcementController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policy: PolicyEvaluator,
    private val settings: SettingsRepository,
    private val rules: RulesRepository,
    private val usage: UsageRepository,
    private val launchController: LaunchController,
    private val notifier: Notifier,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var foregroundPackage: String? = null
    private var recheck: Runnable? = null

    /** Enforcement only does work when the user enabled it and at least one rule could apply. */
    fun isNeeded(now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val s = settings.settings.value
        if (!s.enforcementEnabled) return false
        return policy.isFocusActive(now) || rules.snapshot.value.hasAnyRules ||
            rules.snapshot.value.focusSchedules.any { it.enabled } || s.focusManualActive
    }

    /** Called when our own home screen is shown: the next app opened is a fresh session. */
    fun resetForeground() {
        foregroundPackage = null
        cancelRecheck()
    }

    fun onForeground(packageName: String, fromAccessibility: Boolean) {
        if (packageName.isBlank()) return
        if (packageName == context.packageName) return // our gate/home windows
        if (isTransient(packageName)) return
        if (packageName == foregroundPackage) return
        foregroundPackage = packageName
        cancelRecheck()
        if (policy.isExempt(packageName)) return
        if (!settings.settings.value.enforcementEnabled) return
        scope.launch { evaluate(packageName, fromAccessibility) }
    }

    private suspend fun evaluate(packageName: String, fromAccessibility: Boolean) {
        when (val decision = policy.decide(packageName)) {
            is LaunchDecision.Allow -> decision.remainingMs?.let { scheduleRecheck(packageName, it, fromAccessibility) }
            is LaunchDecision.Pause, is LaunchDecision.Block -> showGate(packageName, fromAccessibility)
        }
    }

    private fun showGate(packageName: String, fromAccessibility: Boolean) {
        handler.post {
            // Background activity starts are allowed for a bound accessibility service, or with
            // the overlay permission. Otherwise fall back to a tappable notification.
            if (fromAccessibility || Settings.canDrawOverlays(context)) {
                launchController.showGateForPackage(packageName, external = true)
            } else {
                notifier.showGateNotification(packageName)
            }
        }
    }

    private fun scheduleRecheck(packageName: String, remainingMs: Long, fromAccessibility: Boolean) {
        cancelRecheck()
        val task = Runnable {
            if (foregroundPackage == packageName) {
                usage.invalidate()
                scope.launch {
                    val decision = policy.decide(packageName)
                    val remaining = (decision as? LaunchDecision.Allow)?.remainingMs
                    if (decision is LaunchDecision.Block && foregroundPackage == packageName) {
                        showGate(packageName, fromAccessibility)
                    } else if (remaining != null && remaining > 0) {
                        // Usage stats lag slightly behind; check again shortly.
                        scheduleRecheck(packageName, remaining, fromAccessibility)
                    }
                }
            }
        }
        recheck = task
        handler.postDelayed(task, remainingMs.coerceAtLeast(MIN_RECHECK_MS) + GRACE_MS)
    }

    private fun cancelRecheck() {
        recheck?.let { handler.removeCallbacks(it) }
        recheck = null
    }

    private fun isTransient(packageName: String): Boolean {
        if (packageName in TRANSIENT) return true
        val ime = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        }.getOrNull()
        return packageName == ime
    }

    companion object {
        private const val GRACE_MS = 1_500L
        private const val MIN_RECHECK_MS = 5_000L
        private val TRANSIENT = setOf("android", "com.android.systemui")
    }
}
