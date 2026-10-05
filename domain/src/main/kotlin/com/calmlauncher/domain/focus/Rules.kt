package com.calmlauncher.domain.focus

import java.time.Instant
import java.time.ZonedDateTime

/** What a rule applies to: a single package, or a user-defined group (category). */
sealed interface RuleTarget {
    val storageKey: String

    data class App(val packageName: String) : RuleTarget {
        override val storageKey: String get() = "app:$packageName"
    }

    data class Group(val categoryId: Long) : RuleTarget {
        override val storageKey: String get() = "group:$categoryId"
    }

    companion object {
        fun parse(key: String): RuleTarget? = when {
            key.startsWith("app:") -> key.removePrefix("app:").takeIf { it.isNotBlank() }?.let { App(it) }
            key.startsWith("group:") -> key.removePrefix("group:").toLongOrNull()?.let { Group(it) }
            else -> null
        }
    }
}

/** User-defined distraction rule. All fields are optional behaviours; nothing is forced. */
data class FocusRule(
    val target: RuleTarget,
    val pauseEnabled: Boolean = false,
    val pauseSeconds: Int = DEFAULT_PAUSE_SECONDS,
    val askReason: Boolean = true,
    val dailyLimitMinutes: Int? = null,
    val alwaysBlocked: Boolean = false,
    val blockWindows: List<TimeWindow> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !pauseEnabled && dailyLimitMinutes == null && !alwaysBlocked && blockWindows.isEmpty()

    companion object {
        const val DEFAULT_PAUSE_SECONDS = 5
        const val MIN_PAUSE_SECONDS = 3
        const val MAX_PAUSE_SECONDS = 15
    }
}

/** Focus mode: a manual toggle (optionally timed) plus any number of weekly schedules. */
data class FocusModeState(
    val manualActive: Boolean = false,
    /** Epoch millis when a timed manual session ends; null = until turned off. */
    val manualUntil: Long? = null,
    val schedules: List<TimeWindow> = emptyList(),
    val allowlist: Set<String> = emptySet(),
    val strict: Boolean = false,
) {
    fun isManualActive(now: ZonedDateTime): Boolean {
        if (!manualActive) return false
        val until = manualUntil ?: return true
        return now.toInstant().isBefore(Instant.ofEpochMilli(until))
    }

    fun activeSchedule(now: ZonedDateTime): TimeWindow? =
        schedules.firstOrNull { it.isActive(now.toLocalDateTime()) }

    fun isActive(now: ZonedDateTime): Boolean = isManualActive(now) || activeSchedule(now) != null

    fun allows(packageName: String): Boolean = packageName in allowlist

    /** When the current focus session ends, if it is knowable. */
    fun activeUntil(now: ZonedDateTime): ZonedDateTime? {
        if (isManualActive(now)) {
            return manualUntil?.let { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), now.zone) }
        }
        val window = activeSchedule(now) ?: return null
        return window.nextBoundary(now)
    }
}

/** Per-day extension the user granted at a limit screen. */
data class LimitExtension(
    val extraMinutes: Int = 0,
    val unlimitedToday: Boolean = false,
)

enum class PassKind {
    /** User already went through the mindful pause; don't pause again for a while. */
    PAUSE,

    /** User deliberately overrode a block (with friction). */
    OVERRIDE,
}
