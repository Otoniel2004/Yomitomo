package io.github.otoniel2004.mangareaderjp.reader.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
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
 *   swipe  → onDrag       (the service replays it on the app underneath)
 *
 * Positions are reported in screen pixels.
 */
class TouchTargets(context: Context, private val listener: Listener) {
    interface Listener {
        fun onTap(block: Int, screenX: Int, screenY: Int)
        fun onDoubleTap(block: Int, screenX: Int, screenY: Int)
        fun onDrag(drag: Drag)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val views = mutableListOf<View>()
    private val slop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, SLOP_DP, appContext.resources.displayMetrics)

    fun setTargets(boxes: List<Box>) = main.post {
        removeAll()
        boxes.forEachIndexed { index, box ->
            if (box.width <= 0 || box.height <= 0) return@forEachIndexed
            val target = TargetView(appContext, index, listener, slop)
            val params = WindowManager.LayoutParams(
                box.width, box.height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                BASE_FLAGS,
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

    /** While true, touches (including a replayed swipe) go through the targets to the app below. */
    fun setPassThrough(passThrough: Boolean) = main.post {
        for (view in views) {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: continue
            params.flags = if (passThrough) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    fun clear() = main.post { removeAll() }

    private fun removeAll() {
        views.forEach { runCatching { windowManager.removeView(it) } }
        views.clear()
    }

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private class TargetView(
        context: Context,
        private val block: Int,
        private val listener: Listener,
        slop: Float,
    ) : View(context) {
        private val drags = DragTracker(slop)
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

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> drags.down(event.rawX, event.rawY, event.eventTime)
                MotionEvent.ACTION_MOVE -> {
                    val wasDragging = drags.isDragging
                    drags.move(event.rawX, event.rawY)
                    // Once it's a swipe, make sure it can't also count as a tap.
                    if (drags.isDragging && !wasDragging) {
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        gestures.onTouchEvent(cancel)
                        cancel.recycle()
                    }
                }
                MotionEvent.ACTION_UP -> drags.up(event.rawX, event.rawY, event.eventTime)?.let {
                    listener.onDrag(it)
                    return true
                }
            }
            return if (drags.isDragging) true else gestures.onTouchEvent(event)
        }
    }

    private companion object {
        const val SLOP_DP = 12f
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
