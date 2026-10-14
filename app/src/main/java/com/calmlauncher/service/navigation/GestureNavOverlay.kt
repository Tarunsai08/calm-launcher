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
    val previousApp: Boolean,
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
 * home / recent apps through the accessibility service's global actions. A plain tap on a
 * strip is handed back to the app underneath, so the strips don't steal touches.
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
    private val views = mutableListOf<EdgeView>()
    private var config: NavOverlayConfig? = null

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
    }

    private fun attach(c: NavOverlayConfig) {
        val metrics = screenSize()
        val density = service.resources.displayMetrics.density
        val edgeW = (c.edgeWidthDp.coerceIn(8, 40) * density).toInt()
        val bottomH = (c.bottomHeightDp.coerceIn(8, 40) * density).toInt()
        val thresholds = NavGestureThresholds(
            triggerPx = 36 * density,
            recentsPx = metrics.second * 0.28f,
            tapSlopPx = 10 * density,
            holdSlopPx = 6 * density,
        )
        if (c.sides) {
            // Middle part of the screen only: keeps the status bar, top app bars and the keyboard
            // area free for normal touches.
            val top = (metrics.second * 0.12f).toInt()
            val height = (metrics.second * 0.55f).toInt()
            add(EdgeView(service, EdgeZone.LEFT, thresholds, c), edgeW, height, Gravity.TOP or Gravity.START, 0, top)
            add(EdgeView(service, EdgeZone.RIGHT, thresholds, c), edgeW, height, Gravity.TOP or Gravity.END, 0, top)
        }
        if (c.bottom) {
            add(EdgeView(service, EdgeZone.BOTTOM, thresholds, c), WindowManager.LayoutParams.MATCH_PARENT, bottomH, Gravity.BOTTOM, 0, 0)
        }
    }

    private fun add(view: EdgeView, width: Int, height: Int, gravity: Int, x: Int, y: Int) {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            this.gravity = gravity
            this.x = x
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
        view.params = params
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

    /** Hands a tap that landed on a strip to whatever is underneath it. */
    private fun passThroughTap(view: EdgeView, rawX: Float, rawY: Float) {
        val params = view.params ?: return
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(view, params) }
        val restore = Runnable {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (view.isAttachedToWindow) runCatching { wm.updateViewLayout(view, params) }
        }
        handler.postDelayed({
            val path = Path().apply { moveTo(rawX.coerceAtLeast(0f), rawY.coerceAtLeast(0f)) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
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
            handler.postDelayed(restore, RESTORE_FALLBACK_MS)
        }, WINDOW_UPDATE_MS)
    }

    @SuppressLint("ViewConstructor")
    private inner class EdgeView(
        context: Context,
        private val zone: EdgeZone,
        thresholds: NavGestureThresholds,
        private val c: NavOverlayConfig,
    ) : View(context) {
        var params: WindowManager.LayoutParams? = null
        private val tracker = NavGestureTracker(zone, thresholds, previousAppEnabled = c.previousApp)
        private var lastCandidate = NavGesture.NONE
        private val holdTicker = object : Runnable {
            override fun run() {
                feedback(tracker.tick(SystemClock.uptimeMillis()))
                handler.postDelayed(this, TICK_MS)
            }
        }
        private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99888888.toInt() }
        private val handleRect = RectF()

        init {
            contentDescription = null
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (zone != EdgeZone.BOTTOM || !c.showHandle) return
            val d = resources.displayMetrics.density
            val w = 108 * d
            val h = 4 * d
            handleRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            canvas.drawRoundRect(handleRect, h / 2, h / 2, handlePaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.down(event.rawX, event.rawY, event.eventTime)
                    lastCandidate = NavGesture.NONE
                    if (zone == EdgeZone.BOTTOM) handler.postDelayed(holdTicker, TICK_MS)
                }
                MotionEvent.ACTION_MOVE -> feedback(tracker.move(event.rawX, event.rawY, event.eventTime))
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(holdTicker)
                    when (val result = tracker.up(event.rawX, event.rawY, event.eventTime)) {
                        NavGesture.NONE -> Unit
                        NavGesture.TAP -> passThroughTap(this, event.rawX, event.rawY)
                        else -> onGesture(result)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdTicker)
                    tracker.cancel()
                }
            }
            return true
        }

        private fun feedback(candidate: NavGesture) {
            if (candidate != lastCandidate && candidate != NavGesture.NONE && c.haptics) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            lastCandidate = candidate
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacks(holdTicker)
            super.onDetachedFromWindow()
        }
    }

    companion object {
        private const val TAP_MS = 10L
        private const val TICK_MS = 50L
        private const val WINDOW_UPDATE_MS = 40L
        private const val RESTORE_FALLBACK_MS = 600L
    }
}
