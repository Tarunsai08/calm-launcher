package com.calmlauncher.service.navigation

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.calmlauncher.domain.navigation.EdgeZone
import com.calmlauncher.domain.navigation.NavGesture
import com.calmlauncher.domain.navigation.NavGestureThresholds
import com.calmlauncher.domain.navigation.NavGestureTracker
import kotlin.math.exp

/** What the overlay should show, derived from settings and the current system state. */
data class NavOverlayConfig(
    val sides: Boolean,
    val bottom: Boolean,
    val quickSwitch: Boolean,
    val edgeWidthDp: Int,
    val bottomHeightDp: Int,
    val haptics: Boolean,
    val showHandle: Boolean,
) {
    val isEmpty: Boolean get() = !sides && !bottom
}

/**
 * Thin, invisible touch strips along the left, right and bottom edges, drawn as accessibility
 * overlay windows (no "display over other apps" permission needed). Swipes become back /
 * home / recent apps / app switching, with the same feel as stock gesture navigation:
 * a back arrow follows the finger, recents open as soon as the finger pauses, and taps or
 * long presses on a strip are handed back to the app underneath.
 *
 * The strips never read or record anything on screen; they only see touches that start on
 * them.
 */
class GestureNavOverlay(
    private val service: AccessibilityService,
    private val onGesture: (NavGesture) -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val views = mutableListOf<View>()
    private var config: NavOverlayConfig? = null
    private var arrow: BackArrowView? = null

    fun apply(newConfig: NavOverlayConfig?) {
        if (newConfig == config) return
        detach()
        config = newConfig
        if (newConfig == null || newConfig.isEmpty) return
        attach(newConfig)
    }

    /** Re-measure after rotation or a display size change. */
    fun relayout() {
        val current = config ?: return
        detach()
        attach(current)
    }

    fun detach() {
        for (v in views) runCatching { wm.removeView(v) }
        views.clear()
        arrow = null
    }

    private fun dp(value: Float) = value * service.resources.displayMetrics.density

    private fun attach(c: NavOverlayConfig) {
        val (screenW, screenH) = screenSize()
        val edgeW = dp(c.edgeWidthDp.coerceIn(8, 40).toFloat()).toInt()
        val bottomH = dp(c.bottomHeightDp.coerceIn(8, 40).toFloat()).toInt()
        val thresholds = NavGestureThresholds(
            backTriggerPx = dp(36f),
            homeTriggerPx = dp(44f),
            pauseMinUpPx = dp(64f),
            switchTriggerPx = minOf(dp(48f), screenW * 0.12f),
            tapSlopPx = dp(8f),
            flingSpeed = dp(0.5f),
            pauseSpeed = dp(0.12f),
            unpauseSpeed = dp(0.45f),
        )
        if (c.sides) {
            // Like stock Android: the whole height except the status bar and the bottom strip.
            val top = (screenH * 0.08f).toInt()
            val height = screenH - top - (if (c.bottom) bottomH else 0)
            add(EdgeView(EdgeZone.LEFT, thresholds, c), edgeW, height, Gravity.TOP or Gravity.START, top, touchable = true)
            add(EdgeView(EdgeZone.RIGHT, thresholds, c), edgeW, height, Gravity.TOP or Gravity.END, top, touchable = true)
            val a = BackArrowView(service)
            add(a, dp(ARROW_W_DP).toInt(), dp(ARROW_H_DP).toInt(), Gravity.TOP or Gravity.START, 0, touchable = false)
            a.visibility = View.GONE
            arrow = a
        }
        if (c.bottom) {
            add(EdgeView(EdgeZone.BOTTOM, thresholds, c), WindowManager.LayoutParams.MATCH_PARENT, bottomH, Gravity.BOTTOM, 0, touchable = true)
        }
    }

    private fun add(view: View, width: Int, height: Int, gravity: Int, y: Int, touchable: Boolean) {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            this.y = y
            title = "CalmLauncherGestures"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Sit on the real screen edge, not above the (hidden) navigation bar.
                fitInsetsTypes = 0
            }
        }
        view.tag = params
        if (runCatching { wm.addView(view, params) }.isSuccess) views += view
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            return b.width() to b.height()
        }
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    /**
     * HyperOS-style back indicator: a black bulge that grows out of the edge where the finger
     * started, with a small white chevron that appears once back is armed.
     */
    private fun showArrow(zone: EdgeZone, rawY: Float, progressPx: Float, armed: Boolean) {
        val a = arrow ?: return
        val params = a.tag as? WindowManager.LayoutParams ?: return
        if (!a.showing) {
            // Anchored where the swipe started, like the system's.
            params.gravity = Gravity.TOP or if (zone == EdgeZone.LEFT) Gravity.START else Gravity.END
            params.y = (rawY - dp(ARROW_H_DP) / 2).toInt()
            a.visibility = View.VISIBLE
            runCatching { wm.updateViewLayout(a, params) }
        }
        a.update(zone == EdgeZone.LEFT, rawY - params.y, progressPx, armed)
    }

    private fun hideArrow() {
        arrow?.retract()
    }

    /** Hands a tap or long press that landed on a strip to whatever is underneath it. */
    private fun passThrough(view: View, rawX: Float, rawY: Float, durationMs: Long) {
        val params = view.tag as? WindowManager.LayoutParams ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(view, params) }
        val restore = Runnable {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
        }
        handler.postDelayed({
            val path = Path().apply { moveTo(rawX.coerceAtLeast(0f), rawY.coerceAtLeast(0f)) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()
            val dispatched = runCatching {
                service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            handler.post(restore)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            handler.post(restore)
                        }
                    },
                    handler,
                )
            }.getOrDefault(false)
            if (!dispatched) handler.post(restore)
            // Belt and braces: never leave a strip untouchable.
            handler.postDelayed(restore, durationMs + RESTORE_FALLBACK_MS)
        }, WINDOW_UPDATE_MS)
    }

    @SuppressLint("ViewConstructor")
    private inner class EdgeView(
        private val zone: EdgeZone,
        thresholds: NavGestureThresholds,
        private val c: NavOverlayConfig,
    ) : View(service) {
        private val tracker = NavGestureTracker(zone, thresholds, quickSwitchEnabled = c.quickSwitch)
        private var lastCandidate = NavGesture.NONE
        private var lastRawX = 0f
        private var lastRawY = 0f

        /** The touch already did its job (recents opened early, or handed to the app). */
        private var consumed = false
        private val ticker = object : Runnable {
            override fun run() {
                onCandidate(tracker.tick(SystemClock.uptimeMillis()))
                if (!consumed) handler.postDelayed(this, TICK_MS)
            }
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99888888.toInt() }
        private val handleRect = RectF()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (zone != EdgeZone.BOTTOM || !c.showHandle) return
            val w = dp(108f)
            val h = dp(4f)
            handleRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawRoundRect(handleRect, h / 2, h / 2, handlePaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            lastRawX = event.rawX
            lastRawY = event.rawY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    consumed = false
                    lastCandidate = NavGesture.NONE
                    tracker.down(event.rawX, event.rawY, event.eventTime)
                    handler.postDelayed(ticker, TICK_MS)
                }
                MotionEvent.ACTION_MOVE -> if (!consumed) onCandidate(tracker.move(event.rawX, event.rawY, event.eventTime))
                MotionEvent.ACTION_UP -> {
                    finishTouch()
                    if (!consumed) {
                        when (val result = tracker.up(event.rawX, event.rawY, event.eventTime)) {
                            NavGesture.NONE, NavGesture.LONG_PRESS -> Unit
                            NavGesture.TAP -> passThrough(this, event.rawX, event.rawY, TAP_MS)
                            else -> onGesture(result)
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    finishTouch()
                    tracker.cancel()
                }
            }
            return true
        }

        private fun finishTouch() {
            handler.removeCallbacks(ticker)
            if (zone != EdgeZone.BOTTOM) hideArrow()
        }

        private fun onCandidate(candidate: NavGesture) {
            if (consumed) return
            if (zone != EdgeZone.BOTTOM) {
                if (tracker.progressPx > 0f && candidate != NavGesture.LONG_PRESS) {
                    showArrow(zone, lastRawY, tracker.progressPx, armed = candidate == NavGesture.BACK)
                } else {
                    hideArrow()
                }
            }
            when (candidate) {
                NavGesture.LONG_PRESS -> {
                    consumed = true
                    handler.removeCallbacks(ticker)
                    hideArrow()
                    passThrough(this, lastRawX, lastRawY, LONG_PRESS_MS)
                }
                // Stock gesture nav shows recents as soon as the finger pauses.
                NavGesture.RECENTS -> {
                    consumed = true
                    handler.removeCallbacks(ticker)
                    buzz()
                    onGesture(NavGesture.RECENTS)
                }
                NavGesture.BACK -> if (lastCandidate != NavGesture.BACK) buzz()
                else -> Unit
            }
            lastCandidate = candidate
        }

        private fun buzz() {
            if (c.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(ticker)
            super.onDetachedFromWindow()
        }
    }

    /** Black edge bulge with a white chevron, drawn like the HyperOS back gesture. */
    private inner class BackArrowView(context: Context) : View(context) {
        var showing = false
            private set
        private var fromLeft = true
        private var bulgeW = 0f
        private var centerY = 0f
        private var armed = false
        private var chevronAlpha = 0f
        private var animator: ValueAnimator? = null
        private val bulge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2000000.toInt() }
        private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = dp(2f)
        }
        private val path = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun update(fromLeft: Boolean, fingerY: Float, progressPx: Float, armed: Boolean) {
            animator?.cancel()
            showing = true
            this.fromLeft = fromLeft
            // The bulge leans a little toward the finger as it moves up or down.
            val anchor = height / 2f
            centerY = anchor + (fingerY - anchor).coerceIn(-dp(24f), dp(24f))
            // Rubber band: grows quickly at first, then slowly toward its maximum.
            val max = dp(BULGE_MAX_DP)
            bulgeW = max * (1f - exp(-progressPx / (max * 1.6f)))
            this.armed = armed
            chevronAlpha = if (armed) 1f else ((progressPx - dp(12f)) / dp(40f)).coerceIn(0f, 0.6f)
            invalidate()
        }

        /** Shrinks back into the edge, like the system does on release or cancel. */
        fun retract() {
            if (!showing) return
            showing = false
            animator?.cancel()
            val from = bulgeW
            animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = RETRACT_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    bulgeW = from * f
                    chevronAlpha *= f
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!showing) visibility = GONE
                    }
                })
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            if (bulgeW < 0.5f) return
            val h = dp(BULGE_H_DP) / 2
            val edge = if (fromLeft) 0f else getWidth().toFloat()
            val dir = if (fromLeft) 1f else -1f
            val peak = edge + dir * bulgeW
            // Smooth bell curve, flat against the edge at both ends.
            path.reset()
            path.moveTo(edge, centerY - h)
            path.cubicTo(edge, centerY - h * 0.45f, peak, centerY - h * 0.38f, peak, centerY)
            path.cubicTo(peak, centerY + h * 0.38f, edge, centerY + h * 0.45f, edge, centerY + h)
            path.close()
            canvas.drawPath(path, bulge)
            if (chevronAlpha <= 0.01f || bulgeW < dp(10f)) return
            chevron.alpha = (chevronAlpha * 255).toInt()
            val cx = edge + dir * bulgeW * 0.48f
            val s = dp(4.5f)
            // Points toward the edge, like the system's.
            path.reset()
            path.moveTo(cx + dir * s * 0.55f, centerY - s)
            path.lineTo(cx - dir * s * 0.55f, centerY)
            path.lineTo(cx + dir * s * 0.55f, centerY + s)
            canvas.drawPath(path, chevron)
        }
    }

    companion object {
        private const val TAP_MS = 10L
        private const val LONG_PRESS_MS = 700L
        private const val TICK_MS = 30L
        private const val WINDOW_UPDATE_MS = 40L
        private const val RESTORE_FALLBACK_MS = 600L
        private const val BULGE_MAX_DP = 26f
        private const val BULGE_H_DP = 210f
        private const val ARROW_W_DP = BULGE_MAX_DP + 4f
        private const val ARROW_H_DP = BULGE_H_DP + 64f
        private const val RETRACT_MS = 180L
    }
}
