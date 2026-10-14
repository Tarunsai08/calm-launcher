package com.calmlauncher.domain.navigation

import kotlin.math.abs

/** Where a navigation gesture starts. */
enum class EdgeZone { LEFT, RIGHT, BOTTOM }

/** What a finished (or in-progress) touch on an edge means. */
enum class NavGesture { NONE, TAP, BACK, HOME, RECENTS, PREVIOUS_APP }

/**
 * Distances in pixels, times in milliseconds. Built from dp values by the caller so this class
 * stays free of Android types and can be unit-tested on the JVM.
 */
data class NavGestureThresholds(
    /** How far a finger must travel inward (sides) or upward (bottom) to count. */
    val triggerPx: Float,
    /** Upward travel from the bottom edge that turns "home" into "recent apps". */
    val recentsPx: Float,
    /** Movement below this is a tap, which is passed through to the app underneath. */
    val tapSlopPx: Float,
    /** While holding after a swipe up, movement below this counts as "holding still". */
    val holdSlopPx: Float,
    /** Holding still this long after swiping up from the bottom opens recent apps. */
    val holdMs: Long = 300L,
)

/**
 * Turns one touch sequence on an edge strip into a navigation gesture, like the system
 * gesture navigation does:
 * - side edges: swipe inward → back (swipe back out again to cancel);
 * - bottom edge: swipe up → home; swipe up further, or swipe up and hold → recent apps;
 *   swipe sideways along the bottom → previous app (if enabled).
 *
 * Feed it [down], then [move] for every move event, [tick] while the finger is held still
 * (move events stop arriving then), and finally [up]. [candidate] is the gesture that would
 * happen if the finger lifted now, so callers can give haptic feedback when it changes.
 */
class NavGestureTracker(
    private val zone: EdgeZone,
    private val t: NavGestureThresholds,
    private val previousAppEnabled: Boolean = false,
) {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var maxTravel = 0f
    private var stillSince = 0L
    private var lastStillX = 0f
    private var lastStillY = 0f

    var candidate: NavGesture = NavGesture.NONE
        private set

    fun down(x: Float, y: Float, timeMs: Long) {
        downX = x
        downY = y
        lastX = x
        lastY = y
        lastStillX = x
        lastStillY = y
        stillSince = timeMs
        maxTravel = 0f
        candidate = NavGesture.NONE
    }

    fun move(x: Float, y: Float, timeMs: Long): NavGesture {
        lastX = x
        lastY = y
        val travel = maxOf(abs(x - downX), abs(y - downY))
        if (travel > maxTravel) maxTravel = travel
        if (abs(x - lastStillX) > t.holdSlopPx || abs(y - lastStillY) > t.holdSlopPx) {
            lastStillX = x
            lastStillY = y
            stillSince = timeMs
        }
        candidate = evaluate(timeMs)
        return candidate
    }

    /** Call periodically while the finger is down; lets "swipe up and hold" become recents. */
    fun tick(timeMs: Long): NavGesture {
        candidate = evaluate(timeMs)
        return candidate
    }

    fun up(x: Float, y: Float, timeMs: Long): NavGesture {
        move(x, y, timeMs)
        val result = when {
            candidate != NavGesture.NONE -> candidate
            maxTravel <= t.tapSlopPx -> NavGesture.TAP
            else -> NavGesture.NONE
        }
        candidate = NavGesture.NONE
        return result
    }

    fun cancel() {
        candidate = NavGesture.NONE
    }

    private fun evaluate(timeMs: Long): NavGesture = when (zone) {
        EdgeZone.LEFT -> side(inward = lastX - downX)
        EdgeZone.RIGHT -> side(inward = downX - lastX)
        EdgeZone.BOTTOM -> bottom(timeMs)
    }

    private fun side(inward: Float): NavGesture {
        val vertical = abs(lastY - downY)
        return if (inward >= t.triggerPx && vertical <= inward * 1.5f) NavGesture.BACK else NavGesture.NONE
    }

    private fun bottom(timeMs: Long): NavGesture {
        val up = downY - lastY
        val sideways = lastX - downX
        return when {
            up >= t.recentsPx -> NavGesture.RECENTS
            up >= t.triggerPx && candidate.isHomeOrRecents() && timeMs - stillSince >= t.holdMs -> NavGesture.RECENTS
            up >= t.triggerPx && candidate == NavGesture.RECENTS -> NavGesture.RECENTS
            up >= t.triggerPx -> NavGesture.HOME
            previousAppEnabled && abs(sideways) >= t.triggerPx * 2 && up < t.triggerPx -> NavGesture.PREVIOUS_APP
            else -> NavGesture.NONE
        }
    }

    private fun NavGesture.isHomeOrRecents() = this == NavGesture.HOME || this == NavGesture.RECENTS
}
