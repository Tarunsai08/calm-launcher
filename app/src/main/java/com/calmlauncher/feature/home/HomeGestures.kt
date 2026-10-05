package com.calmlauncher.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class SwipeDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Detects the four home swipes without stealing system gestures:
 * - Touches that start inside the system gesture insets (edge back-swipe zones, the bottom
 *   home-gesture bar) are ignored entirely, so system back/home always win.
 * - Runs in the Initial pass so it also works when the finger starts on a label, but it lets a
 *   scrollable child keep vertical drags it can actually scroll ([childCanScroll]).
 * - Once a swipe is recognised the rest of the gesture is consumed so labels don't also click.
 */
@Composable
fun Modifier.homeSwipes(
    enabled: Boolean,
    childCanScroll: (SwipeDirection, Offset) -> Boolean,
    onSwipe: (SwipeDirection) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val gestureInsets = WindowInsets.systemGestures
    val left = gestureInsets.getLeft(density, layoutDirection)
    val right = gestureInsets.getRight(density, layoutDirection)
    val bottom = gestureInsets.getBottom(density)
    val top = gestureInsets.getTop(density)
    val threshold = with(density) { 56.dp.toPx() }
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentCanScroll by rememberUpdatedState(childCanScroll)
    if (!enabled) return this
    return this.pointerInput(left, right, bottom, top, threshold) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val p = down.position
            val inEdge = p.x < left || p.x > size.width - right || p.y > size.height - bottom || p.y < top
            if (inEdge) return@awaitEachGesture
            var total = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.positionChangeIgnoreConsumed()
                if (total.getDistance() < threshold) continue
                val direction = if (abs(total.x) > abs(total.y)) {
                    if (total.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                } else {
                    if (total.y > 0) SwipeDirection.DOWN else SwipeDirection.UP
                }
                if ((direction == SwipeDirection.UP || direction == SwipeDirection.DOWN) && currentCanScroll(direction, p)) {
                    break // let the list scroll
                }
                // Horizontal swipes must be clearly horizontal to avoid accidental launches.
                if ((direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) && abs(total.y) > abs(total.x) * 0.7f) {
                    break
                }
                change.consume()
                currentOnSwipe(direction)
                // Swallow the remainder of this gesture.
                while (true) {
                    val rest = awaitPointerEvent(PointerEventPass.Initial)
                    rest.changes.forEach { it.consume() }
                    if (rest.changes.none { it.pressed }) break
                }
                break
            }
        }
    }
}
