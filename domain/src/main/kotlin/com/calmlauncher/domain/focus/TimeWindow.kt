package com.calmlauncher.domain.focus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Bitmask helpers for days of the week. Monday = bit 0 … Sunday = bit 6 (ISO order). */
object Days {
    const val NONE = 0
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val EVERY_DAY = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun of(vararg days: DayOfWeek): Int = days.fold(0) { acc, d -> acc or bit(d) }

    fun toList(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { contains(mask, it) }
}

/**
 * A recurring weekly time window, e.g. 22:00–07:00 on weekdays.
 *
 * If [endMinute] <= [startMinute] the window crosses midnight: it *starts* on a selected day
 * and ends the following morning. A window with start == end covers the full 24 hours.
 * Times are wall-clock minutes-of-day in the device's current zone, so DST and time-zone
 * changes are handled by re-evaluating against the current local time.
 */
data class TimeWindow(
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range" }
        require(endMinute in 0 until MINUTES_PER_DAY) { "endMinute out of range" }
    }

    val crossesMidnight: Boolean get() = endMinute <= startMinute

    fun isActive(at: LocalDateTime): Boolean {
        if (daysMask == Days.NONE) return false
        val minute = at.hour * 60 + at.minute
        val today = at.dayOfWeek
        val yesterday = today.minus(1)
        return if (!crossesMidnight) {
            Days.contains(daysMask, today) && minute >= startMinute && minute < endMinute
        } else {
            (Days.contains(daysMask, today) && minute >= startMinute) ||
                (Days.contains(daysMask, yesterday) && minute < endMinute)
        }
    }

    /**
     * The next instant strictly after [from] at which [isActive] may change value
     * (a start or an end boundary). Null if the window never activates.
     */
    fun nextBoundary(from: ZonedDateTime): ZonedDateTime? {
        if (daysMask == Days.NONE) return null
        val zone = from.zone
        var best: ZonedDateTime? = null
        val startDate = from.toLocalDate().minusDays(1)
        for (offset in 0..8) {
            val date = startDate.plusDays(offset.toLong())
            if (!Days.contains(daysMask, date.dayOfWeek)) continue
            val start = at(date, startMinute, zone)
            val endDate = if (crossesMidnight) date.plusDays(1) else date
            val end = at(endDate, endMinute, zone)
            for (candidate in listOf(start, end)) {
                if (candidate.isAfter(from) && (best == null || candidate.isBefore(best))) best = candidate
            }
        }
        return best
    }

    /** Minutes the window lasts. */
    val durationMinutes: Int
        get() = if (crossesMidnight) MINUTES_PER_DAY - startMinute + endMinute else endMinute - startMinute

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun fromTimes(daysMask: Int, start: LocalTime, end: LocalTime) =
            TimeWindow(daysMask, start.hour * 60 + start.minute, end.hour * 60 + end.minute)

        private fun at(date: LocalDate, minute: Int, zone: ZoneId): ZonedDateTime =
            // Wall-clock resolution: a time inside a DST gap is shifted forward by the gap length.
            date.atTime(minute / 60, minute % 60).atZone(zone)

        fun formatMinute(minute: Int, use24h: Boolean): String {
            val h = minute / 60
            val m = minute % 60
            return if (use24h) {
                "%02d:%02d".format(h, m)
            } else {
                val hour12 = if (h % 12 == 0) 12 else h % 12
                val suffix = if (h < 12) "AM" else "PM"
                "%d:%02d %s".format(hour12, m, suffix)
            }
        }
    }
}
