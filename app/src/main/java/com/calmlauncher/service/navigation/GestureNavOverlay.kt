package com.calmlauncher.service.navigation

import android.accessibilityservice.AccessibilityService
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
import com.calmlauncher.domain.navigation.EdgeZone
import com.calmlauncher.domain.navigation.NavGesture
import com.calmlauncher.domain.navigation.NavGestureThresholds
import com.calmlauncher.domain.navigation.NavGestureTracker

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

    /** Back arrow that follows the finger, like Android's back gesture indicator. */
    private fun showArrow(zone: EdgeZone, rawY: Float, progressPx: Float, armed: Boolean) {
        val a = arrow ?: return
        val params = a.tag as? WindowManager.LayoutParams ?: return
        val gravity = Gravity.TOP or if (zone == EdgeZone.LEFT) Gravity.START else Gravity.END
        val y = (rawY - dp(ARROW_H_DP) / 2).toInt()
        if (params.gravity != gravity || params.y != y || a.visibility != View.VISIBLE) {
            params.gravity = gravity
            params.y = y
            a.visibility = View.VISIBLE
            runCatching { wm.updateViewLayout(a, params) }
        }
        a.update(zone == EdgeZone.LEFT, progressPx, armed)
    }

    private fun hideArrow() {
        arrow?.visibility = View.GONE
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

    /** Dark rounded panel with a chevron that stretches as the finger moves in. */
    private inner class BackArrowView(context: Context) : View(context) {
        private var fromLeft = true
        private var progress = 0f
        private var armed = false
        private val panel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202124.toInt() }
        private val chevron = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val rect = RectF()
        private val path = Path()

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun update(fromLeft: Boolean, progressPx: Float, armed: Boolean) {
            this.fromLeft = fromLeft
            progress = progressPx
            this.armed = armed
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val maxW = width.toFloat() - dp(4f)
            val w = (progress * 0.6f).coerceIn(dp(8f), maxW)
            val h = dp(if (armed) 56f else 48f)
            val cy = height / 2f
            val left = if (fromLeft) dp(2f) else width - dp(2f) - w
            rect.set(left, cy - h / 2, left + w, cy + h / 2)
            panel.alpha = if (armed) 235 else (110 + (progress / dp(36f) * 100).toInt()).coerceAtMost(210)
            canvas.drawRoundRect(rect, h / 2, h / 2, panel)
            if (w < dp(20f)) return
            val cx = rect.centerX()
            val s = dp(7f)
            chevron.strokeWidth = dp(if (armed) 3f else 2.5f)
            chevron.alpha = if (armed) 255 else 170
            path.reset()
            val dir = if (fromLeft) 1f else -1f
            path.moveTo(cx + dir * s / 2, cy - s)
            path.lineTo(cx - dir * s / 2, cy)
            path.lineTo(cx + dir * s / 2, cy + s)
            canvas.drawPath(path, chevron)
        }
    }

    companion object {
        private const val TAP_MS = 10L
        private const val LONG_PRESS_MS = 700L
        private const val TICK_MS = 30L
        private const val WINDOW_UPDATE_MS = 40L
        private const val RESTORE_FALLBACK_MS = 600L
        private const val ARROW_W_DP = 72f
        private const val ARROW_H_DP = 96f
    }
}
