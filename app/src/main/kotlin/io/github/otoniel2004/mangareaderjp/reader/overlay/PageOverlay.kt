package io.github.otoniel2004.mangareaderjp.reader.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box
import io.github.otoniel2004.mangareaderjp.reader.page.RubyMark

/**
 * Full-screen, transparent, never-touchable layer that draws on top of the manga app:
 *   - furigana (small white tags with the reading), above horizontal / right of vertical text
 *   - optionally, debug boxes around each text block
 * Coordinates are screen pixels.
 *
 * Android 12+ blocks touches that pass through another app's overlay unless that window's
 * opacity is at most 0.8, so the window is drawn at 0.8: your taps still reach the reader.
 */
class PageOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var view: DrawingView? = null

    fun show() = main.post {
        if (view != null) return@post
        val drawing = DrawingView(appContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            alpha = MAX_PASS_THROUGH_OPACITY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(drawing, params)
        view = drawing
    }

    fun hide() = main.post {
        view?.let { windowManager.removeView(it) }
        view = null
    }

    fun setContent(rubies: List<RubyMark>, boxes: List<Box>) = main.post {
        view?.let {
            it.rubies = rubies
            it.boxes = boxes
            it.invalidate()
        }
    }

    fun clear() = setContent(emptyList(), emptyList())

    @SuppressLint("ViewConstructor")
    private class DrawingView(context: Context) : View(context) {
        var rubies: List<RubyMark> = emptyList()
        var boxes: List<Box> = emptyList()

        private val location = IntArray(2)
        private val density = context.resources.displayMetrics.density
        private val tag = RectF()
        private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt() }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1A1A1A.toInt()
            textAlign = Paint.Align.CENTER
        }
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xCC00B8D4.toInt()
            strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, context.resources.displayMetrics)
        }

        override fun onDraw(canvas: Canvas) {
            // The window may not start exactly at the screen's (0, 0): shift into view coordinates.
            getLocationOnScreen(location)
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            for (box in boxes) {
                canvas.drawRect(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), boxPaint)
            }
            rubies.forEach { drawRuby(canvas, it) }
        }

        private fun drawRuby(canvas: Canvas, ruby: RubyMark) {
            val b = ruby.box
            tag.set(b.left - PAD, b.top - PAD, b.right + PAD, b.bottom + PAD)
            canvas.drawRoundRect(tag, 3 * density, 3 * density, tagPaint)
            if (ruby.vertical) {
                val size = b.width.toFloat()
                textPaint.textSize = size * TEXT_FILL
                val metrics = textPaint.fontMetrics
                ruby.text.forEachIndexed { i, c ->
                    val centerY = b.top + size * (i + 0.5f)
                    // In vertical text the long-vowel mark is drawn as a vertical bar.
                    val glyph = if (c == 'ー') "｜" else c.toString()
                    canvas.drawText(glyph, (b.left + b.right) / 2f, centerY - (metrics.ascent + metrics.descent) / 2, textPaint)
                }
            } else {
                textPaint.textSize = b.height * TEXT_FILL
                val metrics = textPaint.fontMetrics
                val centerY = (b.top + b.bottom) / 2f
                canvas.drawText(ruby.text, (b.left + b.right) / 2f, centerY - (metrics.ascent + metrics.descent) / 2, textPaint)
            }
        }

        private companion object {
            const val PAD = 1f
            const val TEXT_FILL = 0.95f
        }
    }

    private companion object {
        const val MAX_PASS_THROUGH_OPACITY = 0.8f
    }
}
