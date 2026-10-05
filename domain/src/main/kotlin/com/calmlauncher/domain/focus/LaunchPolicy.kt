package com.calmlauncher.domain.focus

import java.time.ZonedDateTime

sealed interface BlockReason {
    data object FocusMode : BlockReason
    data class Scheduled(val endsAt: ZonedDateTime?) : BlockReason
    data object AlwaysBlocked : BlockReason
    data class DailyLimit(val usedMinutes: Int, val limitMinutes: Int, val isGroup: Boolean) : BlockReason
}

sealed interface LaunchDecision {
    /** Launch freely. [remainingMs] is set when a daily limit applies, so enforcement can re-check. */
    data class Allow(val remainingMs: Long? = null) : LaunchDecision
    data class Pause(val seconds: Int, val askReason: Boolean, val remainingMs: Long? = null) : LaunchDecision
    data class Block(val reason: BlockReason) : LaunchDecision
}

data class PolicyInput(
    val packageName: String,
    val now: ZonedDateTime,
    val appRule: FocusRule? = null,
    val groupRule: FocusRule? = null,
    val focus: FocusModeState = FocusModeState(),
    val appUsedTodayMs: Long = 0L,
    val groupUsedTodayMs: Long = 0L,
    val appExtension: LimitExtension? = null,
    val groupExtension: LimitExtension? = null,
    val activePass: PassKind? = null,
    /** Packages that must never be gated (this launcher, Settings, dialer, IME…). */
    val exempt: Boolean = false,
)

/**
 * Pure decision function for "may the user open this app right now?".
 *
 * Priority: exemption > override pass > focus mode > scheduled block > always-blocked >
 * daily limit > mindful pause > allow.
 */
object LaunchPolicy {

    fun decide(input: PolicyInput): LaunchDecision {
        if (input.exempt) return LaunchDecision.Allow()
        if (input.activePass == PassKind.OVERRIDE) return LaunchDecision.Allow()

        val now = input.now
        if (input.focus.isActive(now) && !input.focus.allows(input.packageName)) {
            return LaunchDecision.Block(BlockReason.FocusMode)
        }

        val rules = listOfNotNull(input.appRule, input.groupRule)
        val activeWindow = rules.flatMap { it.blockWindows }.firstOrNull { it.isActive(now.toLocalDateTime()) }
        if (activeWindow != null) {
            return LaunchDecision.Block(BlockReason.Scheduled(activeWindow.nextBoundary(now)))
        }

        if (rules.any { it.alwaysBlocked }) return LaunchDecision.Block(BlockReason.AlwaysBlocked)

        val appLimit = LimitCalculator.status(input.appRule?.dailyLimitMinutes, input.appUsedTodayMs, input.appExtension)
        if (appLimit != null && appLimit.reached) {
            return LaunchDecision.Block(BlockReason.DailyLimit(appLimit.usedMinutes, appLimit.effectiveLimitMinutes, false))
        }
        val groupLimit = LimitCalculator.status(input.groupRule?.dailyLimitMinutes, input.groupUsedTodayMs, input.groupExtension)
        if (groupLimit != null && groupLimit.reached) {
            return LaunchDecision.Block(BlockReason.DailyLimit(groupLimit.usedMinutes, groupLimit.effectiveLimitMinutes, true))
        }
        val remaining = listOfNotNull(appLimit?.remainingMs, groupLimit?.remainingMs).minOrNull()

        val pauseRule = rules.firstOrNull { it.pauseEnabled }
        if (pauseRule != null && input.activePass != PassKind.PAUSE) {
            val seconds = pauseRule.pauseSeconds.coerceIn(FocusRule.MIN_PAUSE_SECONDS, FocusRule.MAX_PAUSE_SECONDS)
            return LaunchDecision.Pause(seconds, pauseRule.askReason, remaining)
        }
        return LaunchDecision.Allow(remaining)
    }
}

data class LimitStatus(
    val usedMinutes: Int,
    val effectiveLimitMinutes: Int,
    val remainingMs: Long?,
    val reached: Boolean,
)

object LimitCalculator {
    private const val MINUTE_MS = 60_000L

    /** Null when no limit applies (no rule, or unlimited for today). */
    fun status(limitMinutes: Int?, usedMs: Long, extension: LimitExtension?): LimitStatus? {
        if (limitMinutes == null || limitMinutes < 0) return null
        if (extension?.unlimitedToday == true) return null
        val effective = limitMinutes + (extension?.extraMinutes ?: 0).coerceAtLeast(0)
        val limitMs = effective * MINUTE_MS
        val used = usedMs.coerceAtLeast(0L)
        val remaining = (limitMs - used).coerceAtLeast(0L)
        return LimitStatus(
            usedMinutes = (used / MINUTE_MS).toInt(),
            effectiveLimitMinutes = effective,
            remainingMs = remaining,
            reached = used >= limitMs,
        )
    }
}
