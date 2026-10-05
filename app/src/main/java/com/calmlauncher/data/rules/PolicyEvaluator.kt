package com.calmlauncher.data.rules

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.domain.focus.FocusModeState
import com.calmlauncher.domain.focus.LaunchDecision
import com.calmlauncher.domain.focus.LaunchPolicy
import com.calmlauncher.domain.focus.PassKind
import com.calmlauncher.domain.focus.PolicyInput
import com.calmlauncher.domain.focus.RuleTarget
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/** Short-lived permissions granted after the user deliberately chose to continue. In memory only. */
class PassStore {
    private data class Pass(val kind: PassKind, val expiresAt: Long)

    private val passes = ConcurrentHashMap<String, Pass>()

    fun grant(packageName: String, kind: PassKind, durationMs: Long) {
        val existing = passes[packageName]
        // Never downgrade an override to a pause pass.
        val newKind = if (existing?.kind == PassKind.OVERRIDE && existing.expiresAt > now()) PassKind.OVERRIDE else kind
        passes[packageName] = Pass(newKind, now() + durationMs)
    }

    fun active(packageName: String): PassKind? {
        val pass = passes[packageName] ?: return null
        if (pass.expiresAt <= now()) {
            passes.remove(packageName)
            return null
        }
        return pass.kind
    }

    fun revokeAll() = passes.clear()

    private fun now() = System.currentTimeMillis()
}

/**
 * Packages that are never paused or blocked, so the launcher can never trap the user:
 * this app, system Settings, the dialer (emergency calls), System UI, the keyboard, and
 * the permission/installer UIs.
 */
class ExemptPackages(private val context: Context) {
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var cachedAt = 0L

    fun isExempt(packageName: String): Boolean {
        if (System.currentTimeMillis() - cachedAt > REFRESH_MS) refresh()
        return packageName in cache || packageName in STATIC
    }

    fun refresh() {
        val set = HashSet<String>()
        set += context.packageName
        runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull()?.let { set += it }
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()?.let { set += it }
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
        }.getOrNull()?.let { set += it }
        cache = set
        cachedAt = System.currentTimeMillis()
    }

    companion object {
        private const val REFRESH_MS = 60_000L
        val STATIC = setOf(
            "android",
            "com.android.settings",
            "com.android.systemui",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",
            "com.miui.securitycenter",
        )
    }
}

class PolicyEvaluator(
    private val settings: SettingsRepository,
    private val rules: RulesRepository,
    private val apps: AppsRepository,
    private val usage: UsageRepository,
    private val passes: PassStore,
    private val exempt: ExemptPackages,
) {
    fun focusState(s: LauncherSettings = settings.settings.value): FocusModeState = FocusModeState(
        manualActive = s.focusManualActive,
        manualUntil = s.focusManualUntil.takeIf { it > 0L },
        schedules = rules.snapshot.value.focusSchedules.filter { it.enabled }.map { it.window },
        allowlist = s.focusAllowlist,
        strict = s.focusStrict,
    )

    fun isFocusActive(now: ZonedDateTime = ZonedDateTime.now()): Boolean = !rulesPaused() && focusState().isActive(now)

    /** True while the user's emergency "pause everything" is in effect. */
    fun rulesPaused(): Boolean = settings.settings.value.rulesPausedUntil > System.currentTimeMillis()

    /** True if the app would be blocked by focus mode right now (used to dim/hide on home). */
    fun blockedByFocus(packageName: String, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (exempt.isExempt(packageName) || rulesPaused()) return false
        val focus = focusState()
        return focus.isActive(now) && !focus.allows(packageName)
    }

    fun isExempt(packageName: String) = exempt.isExempt(packageName)

    suspend fun decide(packageName: String, now: ZonedDateTime = ZonedDateTime.now()): LaunchDecision {
        if (exempt.isExempt(packageName) || rulesPaused()) return LaunchDecision.Allow()
        val snapshot = rules.snapshot.value
        val appTarget = RuleTarget.App(packageName)
        val categoryId = apps.findByPackage(packageName)?.categoryId
        val groupTarget = categoryId?.let { RuleTarget.Group(it) }
        val appRule = snapshot.ruleFor(appTarget)
        val groupRule = groupTarget?.let { snapshot.ruleFor(it) }

        val needsUsage = appRule?.dailyLimitMinutes != null || groupRule?.dailyLimitMinutes != null
        val appUsed = if (needsUsage && appRule?.dailyLimitMinutes != null) usage.usedTodayMs(packageName) else 0L
        val groupUsed = if (groupRule?.dailyLimitMinutes != null && categoryId != null) {
            val groupPackages = apps.apps.value.filter { it.categoryId == categoryId }.map { it.key.packageName }.toSet()
            usage.usedTodayMs(groupPackages)
        } else {
            0L
        }

        return LaunchPolicy.decide(
            PolicyInput(
                packageName = packageName,
                now = now,
                appRule = appRule,
                groupRule = groupRule,
                focus = focusState(),
                appUsedTodayMs = appUsed,
                groupUsedTodayMs = groupUsed,
                appExtension = if (appRule?.dailyLimitMinutes != null) rules.extensionFor(appTarget) else null,
                groupExtension = if (groupRule?.dailyLimitMinutes != null && groupTarget != null) rules.extensionFor(groupTarget) else null,
                activePass = passes.active(packageName),
                exempt = false,
            ),
        )
    }

    /** Group rule target for an app, if it belongs to a category with a limit. */
    fun limitTargetFor(packageName: String, isGroup: Boolean): RuleTarget {
        if (!isGroup) return RuleTarget.App(packageName)
        val categoryId = apps.findByPackage(packageName)?.categoryId ?: return RuleTarget.App(packageName)
        return RuleTarget.Group(categoryId)
    }
}
