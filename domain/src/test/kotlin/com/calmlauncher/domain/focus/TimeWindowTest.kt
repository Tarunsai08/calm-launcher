package com.calmlauncher.domain.focus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeWindowTest {
    // 2026-10-05 is a Monday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun `daytime window on weekdays`() {
        val w = TimeWindow(Days.WEEKDAYS, 9 * 60, 17 * 60)
        assertTrue(w.isActive(at(5, 9)))
        assertTrue(w.isActive(at(5, 16, 59)))
        assertFalse(w.isActive(at(5, 17)))
        assertFalse(w.isActive(at(5, 8, 59)))
        assertFalse(w.isActive(at(10, 12)), "Saturday")
    }

    @Test
    fun `overnight window belongs to the day it starts`() {
        // Friday-only 22:00-07:00 covers Friday night and early Saturday, not Friday morning.
        val w = TimeWindow(Days.of(DayOfWeek.FRIDAY), 22 * 60, 7 * 60)
        assertTrue(w.crossesMidnight)
        assertTrue(w.isActive(at(9, 23)), "Fri 23:00")
        assertTrue(w.isActive(at(10, 6, 59)), "Sat 06:59")
        assertFalse(w.isActive(at(10, 7)), "Sat 07:00")
        assertFalse(w.isActive(at(9, 6)), "Fri 06:00 belongs to Thursday")
        assertEquals(9 * 60, w.durationMinutes)
    }

    @Test
    fun `equal start and end covers the whole day`() {
        val w = TimeWindow(Days.EVERY_DAY, 0, 0)
        assertTrue(w.isActive(at(5, 0)))
        assertTrue(w.isActive(at(5, 23, 59)))
    }

    @Test
    fun `no days never active`() {
        val w = TimeWindow(Days.NONE, 60, 120)
        assertFalse(w.isActive(at(5, 1, 30)))
        assertNull(w.nextBoundary(ZonedDateTime.of(at(5, 0), ZoneId.of("UTC"))))
    }

    @Test
    fun `invalid minutes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(Days.EVERY_DAY, -1, 10) }
        assertThrows(IllegalArgumentException::class.java) { TimeWindow(Days.EVERY_DAY, 0, 1440) }
    }

    @Test
    fun `next boundary finds start then end`() {
        val zone = ZoneId.of("UTC")
        val w = TimeWindow(Days.WEEKDAYS, 9 * 60, 17 * 60)
        val monday8 = ZonedDateTime.of(at(5, 8), zone)
        assertEquals(ZonedDateTime.of(at(5, 9), zone), w.nextBoundary(monday8))
        val monday10 = ZonedDateTime.of(at(5, 10), zone)
        assertEquals(ZonedDateTime.of(at(5, 17), zone), w.nextBoundary(monday10))
        val friday18 = ZonedDateTime.of(at(9, 18), zone)
        assertEquals(ZonedDateTime.of(at(12, 9), zone), w.nextBoundary(friday18), "skips the weekend")
    }

    @Test
    fun `next boundary for overnight window`() {
        val zone = ZoneId.of("UTC")
        val w = TimeWindow(Days.EVERY_DAY, 22 * 60, 7 * 60)
        val night = ZonedDateTime.of(at(5, 23), zone)
        assertEquals(ZonedDateTime.of(at(6, 7), zone), w.nextBoundary(night))
    }

    @Test
    fun `boundaries respect DST gaps`() {
        // Europe/Berlin springs forward on 2026-03-29 at 02:00 -> 03:00.
        val zone = ZoneId.of("Europe/Berlin")
        val w = TimeWindow(Days.EVERY_DAY, 2 * 60 + 30, 6 * 60)
        val before = ZonedDateTime.of(LocalDateTime.of(2026, 3, 29, 1, 0), zone)
        val next = w.nextBoundary(before)!!
        // 02:30 doesn't exist that day; it resolves to 03:30 local.
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
    }

    @Test
    fun `format minutes`() {
        assertEquals("07:05", TimeWindow.formatMinute(7 * 60 + 5, true))
        assertEquals("7:05 AM", TimeWindow.formatMinute(7 * 60 + 5, false))
        assertEquals("12:00 PM", TimeWindow.formatMinute(12 * 60, false))
        assertEquals("12:00 AM", TimeWindow.formatMinute(0, false))
    }

    @Test
    fun `days helpers`() {
        assertEquals(5, Days.toList(Days.WEEKDAYS).size)
        assertTrue(Days.contains(Days.WEEKEND, DayOfWeek.SUNDAY))
        assertFalse(Days.contains(Days.WEEKEND, DayOfWeek.MONDAY))
    }
}
