package com.calmlauncher.domain.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavGestureTrackerTest {
    private val t = NavGestureThresholds(
        backTriggerPx = 40f,
        homeTriggerPx = 60f,
        pauseMinUpPx = 80f,
        switchTriggerPx = 60f,
        tapSlopPx = 10f,
        flingSpeed = 1.0f,
        pauseSpeed = 0.1f,
        unpauseSpeed = 0.5f,
    )

    /** Moves through [points] at [step] ms per point, then lifts at the last one. */
    private fun swipe(zone: EdgeZone, vararg points: Pair<Float, Float>, step: Long = 16): NavGesture {
        val tracker = NavGestureTracker(zone, t)
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
    fun `left and right edge inward swipes are back`() {
        assertEquals(NavGesture.BACK, swipe(EdgeZone.LEFT, 2f to 500f, 30f to 505f, 80f to 510f))
        assertEquals(NavGesture.BACK, swipe(EdgeZone.RIGHT, 1078f to 500f, 1040f to 500f, 1000f to 502f))
    }

    @Test
    fun `short slow or mostly vertical side swipes do nothing`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 14f to 500f, 18f to 500f, step = 100))
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 30f to 600f, 50f to 700f))
    }

    @Test
    fun `quick inward flick counts as back`() {
        assertEquals(NavGesture.BACK, swipe(EdgeZone.LEFT, 2f to 500f, 14f to 500f, 28f to 500f, step = 8))
    }

    @Test
    fun `pulling back to the edge cancels back`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.LEFT, 2f to 500f, 80f to 500f, 40f to 500f, 10f to 500f, step = 60))
    }

    @Test
    fun `tap stays a tap`() {
        assertEquals(NavGesture.TAP, swipe(EdgeZone.BOTTOM, 500f to 2390f, 503f to 2388f))
    }

    @Test
    fun `holding still is a long press`() {
        val tracker = NavGestureTracker(EdgeZone.LEFT, t)
        tracker.down(5f, 500f, 0)
        assertEquals(NavGesture.LONG_PRESS, tracker.tick(500))
    }

    @Test
    fun `swipe up is home`() {
        assertEquals(NavGesture.HOME, swipe(EdgeZone.BOTTOM, 500f to 2390f, 500f to 2340f, 500f to 2250f, 500f to 2150f))
    }

    @Test
    fun `short fast flick up is home`() {
        assertEquals(NavGesture.HOME, swipe(EdgeZone.BOTTOM, 500f to 2390f, 500f to 2375f, 500f to 2350f, step = 8))
    }

    @Test
    fun `swipe up and pause is recents, decided before lifting`() {
        val tracker = NavGestureTracker(EdgeZone.BOTTOM, t)
        tracker.down(500f, 2390f, 0)
        tracker.move(500f, 2300f, 16)
        tracker.move(500f, 2200f, 32)
        tracker.move(500f, 2199f, 100)
        // Finger stays still: no more move events.
        assertEquals(NavGesture.RECENTS, tracker.tick(260))
        assertTrue(tracker.paused)
        assertEquals(NavGesture.RECENTS, tracker.up(500f, 2199f, 400))
    }

    @Test
    fun `slow drag with jitter still pauses into recents`() {
        val tracker = NavGestureTracker(EdgeZone.BOTTOM, t)
        tracker.down(500f, 2390f, 0)
        tracker.move(500f, 2250f, 50)
        var time = 50L
        repeat(15) {
            time += 16
            tracker.move(500f + it % 2, 2250f - it % 2, time)
        }
        assertEquals(NavGesture.RECENTS, tracker.up(500f, 2250f, time + 16))
    }

    @Test
    fun `moving on after a pause and flicking up goes home`() {
        val tracker = NavGestureTracker(EdgeZone.BOTTOM, t)
        tracker.down(500f, 2390f, 0)
        tracker.move(500f, 2250f, 50)
        tracker.tick(300)
        assertTrue(tracker.paused)
        tracker.move(500f, 2150f, 316)
        tracker.move(500f, 2000f, 332)
        assertEquals(NavGesture.HOME, tracker.up(500f, 1850f, 348))
    }

    @Test
    fun `sideways along the bottom switches apps`() {
        assertEquals(NavGesture.SWITCH_PREVIOUS, swipe(EdgeZone.BOTTOM, 300f to 2390f, 360f to 2385f, 450f to 2380f))
        assertEquals(NavGesture.SWITCH_NEXT, swipe(EdgeZone.BOTTOM, 700f to 2390f, 640f to 2385f, 550f to 2380f))
    }

    @Test
    fun `quick switch can be turned off`() {
        val tracker = NavGestureTracker(EdgeZone.BOTTOM, t, quickSwitchEnabled = false)
        tracker.down(300f, 2390f, 0)
        tracker.move(400f, 2385f, 16)
        assertEquals(NavGesture.NONE, tracker.up(450f, 2385f, 32))
    }

    @Test
    fun `downward swipe at the bottom does nothing`() {
        assertEquals(NavGesture.NONE, swipe(EdgeZone.BOTTOM, 500f to 2380f, 500f to 2395f, 500f to 2399f, step = 40))
    }
}
