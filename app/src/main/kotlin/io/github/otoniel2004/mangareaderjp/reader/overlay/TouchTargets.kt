package io.github.otoniel2004.mangareaderjp.reader.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box

/**
 * Invisible "buttons" over each text block (option B): only these areas take touches,
 * everywhere else touches go straight to the manga app, so pages still turn.
 *
 *   1 tap  → onTap        (waits ~0.3 s to make sure it isn't a double tap)
 *   2 taps → onDoubleTap
 *
 * Positions are reported in screen pixels.
 */
class TouchTargets(context: Context, private val listener: Listener) {
    interface Listener {
        fun onTap(block: Int, screenX: Int, screenY: Int)
        fun onDoubleTap(block: Int, screenX: Int, screenY: Int)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val views = mutableListOf<View>()

    fun setTargets(boxes: List<Box>) = main.post {
        removeAll()
        boxes.forEachIndexed { index, box ->
            if (box.width <= 0 || box.height <= 0) return@forEachIndexed
            val target = TargetView(appContext, index, listener)
            val params = WindowManager.LayoutParams(
                box.width, box.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = box.left
                y = box.top
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            windowManager.addView(target, params)
            views += target
        }
    }

    fun clear() = main.post { removeAll() }

    private fun removeAll() {
        views.forEach { runCatching { windowManager.removeView(it) } }
        views.clear()
    }

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private class TargetView(context: Context, private val block: Int, private val listener: Listener) : View(context) {
        private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                listener.onTap(block, e.rawX.toInt(), e.rawY.toInt())
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                listener.onDoubleTap(block, e.rawX.toInt(), e.rawY.toInt())
                return true
            }
        })

        override fun onTouchEvent(event: MotionEvent) = gestures.onTouchEvent(event)
    }
}
