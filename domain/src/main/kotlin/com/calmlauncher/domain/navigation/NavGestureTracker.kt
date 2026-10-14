package com.calmlauncher.domain.navigation

import kotlin.math.abs
import kotlin.math.hypot

/** Where a navigation touch started. */
enum class EdgeZone { LEFT, RIGHT, BOTTOM }

/** What a finished (or in-progress) edge touch means. */
enum class NavGesture { NONE, TAP, LONG_PRESS, BACK, HOME, RECENTS, SWITCH_PREVIOUS, SWITCH_NEXT }

/**
 * Distances in px, speeds in px per millisecond. Defaults mirror stock Android / HyperOS
 * gesture navigation, converted from dp by the caller.
 */
data class NavGestureThresholds(
    /** Inward drag that arms "back" (the arrow turns solid). */
    val backTriggerPx: Float,
    /** Upward drag that counts as "home" on release when no pause happened. */
    val homeTriggerPx: Float,
    /** Upward drag needed before a pause can mean "recent apps". */
    val pauseMinUpPx: Float,
    /** Sideways drag along the bottom that switches apps. */
    val switchTriggerPx: Float,
    /** Movement below this is a tap or long press. */
    val tapSlopPx: Float,
    /** A release faster than this is a fling. */
    val flingSpeed: Float,
    /** Below this speed the finger counts as paused. */
    val pauseSpeed: Float,
    /** Above this speed a pause is undone (the finger moved on). */
    val unpauseSpeed: Float,
    val pauseMs: Long = 110L,
    val longPressMs: Long = 450L,
)

/**
 * Pure gesture recogniser for the built-in navigation strips, modelled on Android's
 * Launcher3 (QuickStep) and SystemUI back gesture:
 * - Side edges: drag inward past the trigger and let go = back. Pulling back toward the edge
 *   before letting go cancels, and a quick inward flick also counts.
 * - Bottom edge: flick or swipe up = home; swipe up and pause = recent apps (decided the
 *   moment the finger pauses, so it also works when the finger is lifted after the pause);
 *   swipe sideways along the bottom = switch to the previous (right) or next (left) app.
 * - Little movement = tap (or long press when held), so the touch can be handed to the app.
 */
class NavGestureTracker(
    private val zone: EdgeZone,
    private val t: NavGestureThresholds,
    private val quickSwitchEnabled: Boolean = true,
) {
    private var startX = 0f
    private var startY = 0f
    private var downTime = 0L
    private var maxTravel = 0f
    private var active = false
    private val samples = ArrayDeque<Sample>()
    private var slowSince = -1L

    /** True while the finger is paused after a long enough upward swipe (recents armed). */
    var paused = false
        private set

    /** What would happen if the finger were lifted now. */
    var candidate = NavGesture.NONE
        private set

    /** Inward distance for side edges (for the back arrow), upward distance for the bottom. */
    var progressPx = 0f
        private set

    var lastY = 0f
        private set

    fun down(x: Float, y: Float, timeMs: Long) {
        startX = x
        startY = y
        downTime = timeMs
        maxTravel = 0f
        active = true
        paused = false
        slowSince = -1L
        candidate = NavGesture.NONE
        progressPx = 0f
        lastY = y
        samples.clear()
        addSample(x, y, timeMs)
    }

    fun move(x: Float, y: Float, timeMs: Long): NavGesture {
        if (!active) return NavGesture.NONE
        addSample(x, y, timeMs)
        lastY = y
        maxTravel = maxOf(maxTravel, hypot(x - startX, y - startY))
        updatePause(x, y, timeMs)
        candidate = evaluate(x, y, release = false, timeMs = timeMs)
        return candidate
    }

    /** Call periodically while the finger is down and still; detects holds and pauses. */
    fun tick(timeMs: Long): NavGesture {
        if (!active) return NavGesture.NONE
        val last = samples.lastOrNull() ?: return candidate
        // No move events means the finger is not moving at all.
        if (timeMs - last.t >= t.pauseMs) {
            if (zone == EdgeZone.BOTTOM && startY - last.y >= t.pauseMinUpPx && !isSideways(last.x, last.y)) paused = true
        }
        candidate = evaluate(last.x, last.y, release = false, timeMs = timeMs)
        return candidate
    }

    fun up(x: Float, y: Float, timeMs: Long): NavGesture {
        if (!active) return NavGesture.NONE
        addSample(x, y, timeMs)
        maxTravel = maxOf(maxTravel, hypot(x - startX, y - startY))
        active = false
        return evaluate(x, y, release = true, timeMs = timeMs)
    }

    fun cancel() {
        active = false
        paused = false
        candidate = NavGesture.NONE
        samples.clear()
    }

    private fun evaluate(x: Float, y: Float, release: Boolean, timeMs: Long): NavGesture {
        if (maxTravel <= t.tapSlopPx) {
            return if (timeMs - downTime >= t.longPressMs) NavGesture.LONG_PRESS else if (release) NavGesture.TAP else NavGesture.NONE
        }
        return when (zone) {
            EdgeZone.LEFT -> side(x - startX, y - startY, release)
            EdgeZone.RIGHT -> side(startX - x, y - startY, release)
            EdgeZone.BOTTOM -> bottom(x, y, release)
        }
    }

    private fun side(inward: Float, vertical: Float, release: Boolean): NavGesture {
        progressPx = inward.coerceAtLeast(0f)
        // A mostly vertical drag along the edge is not a back gesture.
        if (abs(vertical) > inward * 2f && abs(vertical) > t.tapSlopPx * 3) return NavGesture.NONE
        if (inward >= t.backTriggerPx) return NavGesture.BACK
        if (release) {
            val (vx, _) = velocity()
            val inwardSpeed = if (zone == EdgeZone.LEFT) vx else -vx
            if (inwardSpeed >= t.flingSpeed && inward >= t.backTriggerPx / 2) return NavGesture.BACK
        }
        return NavGesture.NONE
    }

    private fun bottom(x: Float, y: Float, release: Boolean): NavGesture {
        val dx = x - startX
        val up = startY - y
        progressPx = up.coerceAtLeast(0f)
        val (vx, vy) = velocity()
        if (quickSwitchEnabled && isSideways(x, y)) {
            if (abs(dx) >= t.switchTriggerPx ||
                (release && abs(vx) >= t.flingSpeed && abs(dx) >= t.switchTriggerPx / 3)
            ) {
                return if (dx > 0) NavGesture.SWITCH_PREVIOUS else NavGesture.SWITCH_NEXT
            }
            return NavGesture.NONE
        }
        val flingUp = -vy >= t.flingSpeed
        return when {
            // A fast flick at the end beats an earlier pause, like on stock Android.
            release && paused && flingUp -> NavGesture.HOME
            paused -> NavGesture.RECENTS
            up >= t.homeTriggerPx -> NavGesture.HOME
            release && flingUp && up >= t.tapSlopPx * 2 -> NavGesture.HOME
            else -> NavGesture.NONE
        }
    }

    private fun isSideways(x: Float, y: Float): Boolean {
        val dx = abs(x - startX)
        val up = startY - y
        return dx > up * 1.2f && !paused
    }

    private fun updatePause(x: Float, y: Float, timeMs: Long) {
        if (zone != EdgeZone.BOTTOM) return
        val (vx, vy) = velocity()
        val speed = hypot(vx, vy)
        val farEnough = startY - y >= t.pauseMinUpPx && !isSideways(x, y)
        if (paused) {
            if (speed > t.unpauseSpeed) {
                paused = false
                slowSince = -1L
            }
            return
        }
        if (farEnough && speed < t.pauseSpeed) {
            if (slowSince < 0) slowSince = timeMs
            if (timeMs - slowSince >= t.pauseMs) paused = true
        } else {
            slowSince = -1L
        }
    }

    private fun addSample(x: Float, y: Float, timeMs: Long) {
        samples.addLast(Sample(x, y, timeMs))
        while (samples.size > 2 && timeMs - samples.first().t > VELOCITY_WINDOW_MS) samples.removeFirst()
    }

    /** Average velocity over the last [VELOCITY_WINDOW_MS], px/ms. */
    private fun velocity(): Pair<Float, Float> {
        if (samples.size < 2) return 0f to 0f
        val a = samples.first()
        val b = samples.last()
        val dt = (b.t - a.t).coerceAtLeast(1L).toFloat()
        return (b.x - a.x) / dt to (b.y - a.y) / dt
    }

    private class Sample(val x: Float, val y: Float, val t: Long)

    private companion object {
        const val VELOCITY_WINDOW_MS = 80L
    }
}
