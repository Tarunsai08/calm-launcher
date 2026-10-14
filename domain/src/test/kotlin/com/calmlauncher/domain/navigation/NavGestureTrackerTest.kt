package com.calmlauncher.domain.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavGestureTrackerTest {
    private val t = NavGestureThresholds(triggerPx = 40f, recentsPx = 400f, tapSlopPx = 10f, holdSlopPx = 8f, holdMs = 300)

    private fun swipe(zone: EdgeZone, vararg points: Pair<Float, Float>, step: Long = 16, previous: Boolean = false): NavGesture {
        val tracker = NavGestureTracker(zone, t, previous)
        var time = 0L
        tracker.down(points.first().first, points.first().second, time)
        for (p in points.drop(1).dropLast(1)) {
            time += step
            tracker.move(p.first, p.second, time)
        }
        time += step
        return tracker.up(points.last().first, points.last().second, time)
    }

    @Test
    fun `left edge inward swipe is back`() {
        assertEquals(NavGesture.BACK, swipe(EdgeZone.LEFT, 2f to 500f, 30f to 505f, 80f to 510f))
    }

    @Test
    fun `right edge inward swipe is back`() {
        assertEquals(NavGesture.BACK, swipe(EdgeZone.RIGHT, 1078f to 500f, 1040f to 500f, 1000f to 502f))
    }

    @Test
    fun `short or mostly vertical side swipes do nothing`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 20f to 500f, 30f to 500f))
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 30f to 600f, 50f to 700f))
    }

    @Test
    fun `swiping back out cancels back`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 80f to 500f, 10f to 500f))
    }

    @Test
    fun `a tap is reported so it can be passed through`() {
        assertEquals(NavGesture.TAP, swipe(EdgeZone.LEFT, 5f to 500f, 7f to 502f))
        assertEquals(NavGesture.TAP, swipe(EdgeZone.BOTTOM, 500f to 2390f, 503f to 2392f))
    }

    @Test
    fun `bottom swipe up is home`() {
        assertEquals(NavGesture.HOME, swipe(EdgeZone.BOTTOM, 540f to 2390f, 540f to 2300f, 540f to 2200f))
    }

    @Test
    fun `long bottom swipe is recents`() {
        assertEquals(NavGesture.RECENTS, swipe(EdgeZone.BOTTOM, 540f to 2390f, 540f to 2100f, 540f to 1900f))
    }

    @Test
    fun `swipe up and hold is recents`() {
        val tracker = NavGestureTracker(EdgeZone.BOTTOM, t)
        tracker.down(540f, 2390f, 0)
        tracker.move(540f, 2300f, 16)
        assertEquals(NavGesture.HOME, tracker.move(540f, 2250f, 32))
        assertEquals(NavGesture.HOME, tracker.tick(200))
        assertEquals(NavGesture.RECENTS, tracker.tick(340))
        assertEquals(NavGesture.RECENTS, tracker.up(541f, 2251f, 360))
    }

    @Test
    fun `dragging back down after arming cancels`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.BOTTOM, 540f to 2390f, 540f to 2300f, 540f to 2380f))
    }

    @Test
    fun `sideways along the bottom is previous app only when enabled`() {
        assertEquals(NavGesture.PREVIOUS_APP, swipe(EdgeZone.BOTTOM, 300f to 2390f, 360f to 2388f, 420f to 2386f, previous = true))
        assertEquals(NavGesture.NONE, swipe(EdgeZone.BOTTOM, 300f to 2390f, 360f to 2388f, 420f to 2386f, previous = false))
    }

    @Test
    fun `cancel clears the candidate`() {
        val tracker = NavGestureTracker(EdgeZone.LEFT, t)
        tracker.down(0f, 0f, 0)
        tracker.move(100f, 0f, 10)
        assertEquals(NavGesture.BACK, tracker.candidate)
        tracker.cancel()
        assertEquals(NavGesture.NONE, tracker.candidate)
    }
}
