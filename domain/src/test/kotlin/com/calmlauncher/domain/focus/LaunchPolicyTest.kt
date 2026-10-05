package com.calmlauncher.domain.focus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class LaunchPolicyTest {
    private val zone = ZoneId.of("UTC")
    private val mondayNoon = ZonedDateTime.of(LocalDateTime.of(2026, 10, 5, 12, 0), zone)
    private val pkg = "com.social"
    private val minute = 60_000L

    private fun input(
        appRule: FocusRule? = null,
        groupRule: FocusRule? = null,
        focus: FocusModeState = FocusModeState(),
        used: Long = 0,
        groupUsed: Long = 0,
        ext: LimitExtension? = null,
        pass: PassKind? = null,
        exempt: Boolean = false,
        now: ZonedDateTime = mondayNoon,
    ) = PolicyInput(pkg, now, appRule, groupRule, focus, used, groupUsed, ext, null, pass, exempt)

    private fun rule(
        pause: Boolean = false,
        limit: Int? = null,
        blocked: Boolean = false,
        windows: List<TimeWindow> = emptyList(),
        seconds: Int = 5,
    ) = FocusRule(RuleTarget.App(pkg), pause, seconds, true, limit, blocked, windows)

    @Test
    fun `no rules means allow`() {
        assertEquals(LaunchDecision.Allow(null), LaunchPolicy.decide(input()))
    }

    @Test
    fun `pause rule pauses until a pause pass exists`() {
        val d = LaunchPolicy.decide(input(appRule = rule(pause = true, seconds = 8)))
        assertEquals(LaunchDecision.Pause(8, true, null), d)
        assertTrue(LaunchPolicy.decide(input(appRule = rule(pause = true), pass = PassKind.PAUSE)) is LaunchDecision.Allow)
    }

    @Test
    fun `pause seconds are clamped to the allowed range`() {
        val d = LaunchPolicy.decide(input(appRule = rule(pause = true, seconds = 99))) as LaunchDecision.Pause
        assertEquals(FocusRule.MAX_PAUSE_SECONDS, d.seconds)
    }

    @Test
    fun `limit blocks when used up and reports remaining otherwise`() {
        val r = rule(limit = 30)
        val blocked = LaunchPolicy.decide(input(appRule = r, used = 30 * minute))
        assertEquals(LaunchDecision.Block(BlockReason.DailyLimit(30, 30, false)), blocked)
        val allowed = LaunchPolicy.decide(input(appRule = r, used = 10 * minute)) as LaunchDecision.Allow
        assertEquals(20 * minute, allowed.remainingMs)
    }

    @Test
    fun `extensions add minutes or lift the limit for the day`() {
        val r = rule(limit = 30)
        assertTrue(LaunchPolicy.decide(input(appRule = r, used = 32 * minute, ext = LimitExtension(5))) is LaunchDecision.Allow)
        assertTrue(LaunchPolicy.decide(input(appRule = r, used = 36 * minute, ext = LimitExtension(5))) is LaunchDecision.Block)
        assertTrue(LaunchPolicy.decide(input(appRule = r, used = 999 * minute, ext = LimitExtension(0, true))) is LaunchDecision.Allow)
    }

    @Test
    fun `group limit applies across apps`() {
        val group = FocusRule(RuleTarget.Group(7), dailyLimitMinutes = 60)
        val d = LaunchPolicy.decide(input(groupRule = group, used = 5 * minute, groupUsed = 61 * minute))
        assertEquals(LaunchDecision.Block(BlockReason.DailyLimit(61, 60, true)), d)
    }

    @Test
    fun `scheduled window blocks with end time`() {
        val w = TimeWindow(Days.EVERY_DAY, 11 * 60, 13 * 60)
        val d = LaunchPolicy.decide(input(appRule = rule(windows = listOf(w)))) as LaunchDecision.Block
        val reason = d.reason as BlockReason.Scheduled
        assertEquals(13, reason.endsAt?.hour)
    }

    @Test
    fun `always blocked`() {
        assertEquals(LaunchDecision.Block(BlockReason.AlwaysBlocked), LaunchPolicy.decide(input(appRule = rule(blocked = true))))
    }

    @Test
    fun `focus mode blocks apps not on the allowlist`() {
        val focus = FocusModeState(manualActive = true)
        assertEquals(LaunchDecision.Block(BlockReason.FocusMode), LaunchPolicy.decide(input(focus = focus)))
        val allowed = focus.copy(allowlist = setOf(pkg))
        assertTrue(LaunchPolicy.decide(input(focus = allowed)) is LaunchDecision.Allow)
    }

    @Test
    fun `timed focus ends by itself`() {
        val until = mondayNoon.minusMinutes(1).toInstant().toEpochMilli()
        val focus = FocusModeState(manualActive = true, manualUntil = until)
        assertFalse(focus.isActive(mondayNoon))
        val later = mondayNoon.plusHours(1).toInstant().toEpochMilli()
        assertTrue(focus.copy(manualUntil = later).isActive(mondayNoon))
        assertEquals(mondayNoon.plusHours(1).toInstant(), focus.copy(manualUntil = later).activeUntil(mondayNoon)?.toInstant())
    }

    @Test
    fun `focus schedules activate focus mode`() {
        val focus = FocusModeState(schedules = listOf(TimeWindow(Days.WEEKDAYS, 9 * 60, 17 * 60)))
        assertTrue(focus.isActive(mondayNoon))
        assertEquals(17, focus.activeUntil(mondayNoon)?.hour)
        assertFalse(focus.isActive(mondayNoon.withHour(18)))
        assertNull(focus.activeUntil(mondayNoon.withHour(18)))
    }

    @Test
    fun `priority order focus then schedule then always then limit then pause`() {
        val w = TimeWindow(Days.EVERY_DAY, 0, 0)
        val everything = rule(pause = true, limit = 1, blocked = true, windows = listOf(w))
        val focus = FocusModeState(manualActive = true)
        assertEquals(BlockReason.FocusMode, (LaunchPolicy.decide(input(appRule = everything, focus = focus, used = 99 * minute)) as LaunchDecision.Block).reason)
        assertTrue((LaunchPolicy.decide(input(appRule = everything, used = 99 * minute)) as LaunchDecision.Block).reason is BlockReason.Scheduled)
        assertEquals(BlockReason.AlwaysBlocked, (LaunchPolicy.decide(input(appRule = everything.copy(blockWindows = emptyList()), used = 99 * minute)) as LaunchDecision.Block).reason)
        assertTrue((LaunchPolicy.decide(input(appRule = rule(pause = true, limit = 1), used = 99 * minute)) as LaunchDecision.Block).reason is BlockReason.DailyLimit)
    }

    @Test
    fun `exempt packages and override passes always allow`() {
        val focus = FocusModeState(manualActive = true)
        assertTrue(LaunchPolicy.decide(input(focus = focus, exempt = true)) is LaunchDecision.Allow)
        assertTrue(LaunchPolicy.decide(input(appRule = rule(blocked = true), pass = PassKind.OVERRIDE)) is LaunchDecision.Allow)
    }

    @Test
    fun `limit calculator handles edge values`() {
        assertNull(LimitCalculator.status(null, 100, null))
        assertNull(LimitCalculator.status(-1, 100, null))
        val s = LimitCalculator.status(0, 0, null)!!
        assertTrue(s.reached)
        val t = LimitCalculator.status(10, -50, LimitExtension(-5))!!
        assertEquals(10, t.effectiveLimitMinutes)
        assertEquals(10 * minute, t.remainingMs)
    }

    @Test
    fun `rule target round trips`() {
        assertEquals(RuleTarget.App("a.b"), RuleTarget.parse("app:a.b"))
        assertEquals(RuleTarget.Group(3), RuleTarget.parse("group:3"))
        assertNull(RuleTarget.parse("group:x"))
        assertNull(RuleTarget.parse("app:"))
        assertNull(RuleTarget.parse("nope"))
        assertEquals("group:3", RuleTarget.Group(3).storageKey)
    }

    @Test
    fun `empty rule detection`() {
        assertTrue(FocusRule(RuleTarget.App(pkg)).isEmpty)
        assertFalse(rule(pause = true).isEmpty)
    }
}
