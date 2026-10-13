package io.github.otoniel2004.mangareaderjp.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box

/**
 * Debug overlay: draws a rectangle around every text block the OCR found.
 * Full-screen, transparent and never touchable. Boxes are in screen pixels.
 * Phase 4 replaces this with furigana and tap targets.
 */
class OcrBoxesOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var view: BoxesView? = null

    fun show() = main.post {
        if (view != null) return@post
        val boxesView = BoxesView(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        windowManager.addView(boxesView, params)
        view = boxesView
    }

    fun hide() = main.post {
        view?.let { windowManager.removeView(it) }
        view = null
    }

    fun setBoxes(boxes: List<Box>) = main.post {
        view?.let {
            it.boxes = boxes
            it.invalidate()
        }
    }

    @SuppressLint("ViewConstructor")
    private class BoxesView(context: Context) : View(context) {
        var boxes: List<Box> = emptyList()
        private val location = IntArray(2)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xCC00B8D4.toInt()
            strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.resources.displayMetrics)
        }

        override fun onDraw(canvas: Canvas) {
            // The window may not start exactly at the screen's (0, 0): shift boxes into view coordinates.
            getLocationOnScreen(location)
            for (box in boxes) {
                canvas.drawRect(
                    (box.left - location[0]).toFloat(), (box.top - location[1]).toFloat(),
                    (box.right - location[0]).toFloat(), (box.bottom - location[1]).toFloat(),
                    paint,
                )
            }
        }
    }
}
