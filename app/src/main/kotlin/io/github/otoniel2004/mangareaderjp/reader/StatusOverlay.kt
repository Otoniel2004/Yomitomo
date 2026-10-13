package io.github.otoniel2004.mangareaderjp.reader

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * The small status dot drawn over other apps (top-right corner):
 *
 *   gray  = waiting for the first page
 *   amber = screen moving / processing
 *   green = page read (briefly shows "Page N · X text blocks")
 *   red   = capture blocked by the app on screen
 *
 * It never takes touches (FLAG_NOT_TOUCHABLE), so it can't get in the way of the reader.
 *
 * The same tiny window also keeps the screen awake while reading (FLAG_KEEP_SCREEN_ON):
 * Android ends screen capture when the screen turns off, so letting it time out would stop the reader.
 * With showDot = false the window stays (for keep-screen-on) but draws nothing.
 * Every method can be called from any thread; work is posted to the main thread.
 */
class StatusOverlay(
    context: Context,
    private val showDot: Boolean,
    private val keepScreenOn: Boolean,
) {
    sealed interface State {
        data object Waiting : State
        data object Working : State
        data class NewPage(val number: Int, val textBlocks: Int) : State
        data object Ready : State
        data object Blocked : State
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var dotColor = GRAY

    private val dropLabel = Runnable { render(null) }

    fun show() = main.post {
        if (view != null) return@post
        val textView = TextView(appContext).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.WHITE)
            val padding = dp(6)
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (keepScreenOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = statusBarHeight()
        }
        windowManager.addView(textView, params)
        view = textView
        render(null)
    }

    fun hide() = main.post {
        main.removeCallbacks(dropLabel)
        view?.let { windowManager.removeView(it) }
        view = null
    }

    fun setState(state: State) = main.post {
        main.removeCallbacks(dropLabel)
        when (state) {
            State.Waiting -> { dotColor = GRAY; render(null) }
            State.Working -> { dotColor = AMBER; render(null) }
            State.Blocked -> { dotColor = RED; render("Capture blocked") }
            State.Ready -> { dotColor = GREEN; render(null) }
            is State.NewPage -> {
                dotColor = GREEN
                render("Page ${state.number} · ${state.textBlocks} text blocks")
                main.postDelayed(dropLabel, LABEL_MILLIS)
            }
        }
    }

    private fun render(label: String?) {
        val textView = view ?: return
        if (!showDot) {
            textView.text = " "
            textView.background = null
            return
        }
        val text = if (label == null) "●" else "●  $label"
        textView.text = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (label == null) Color.TRANSPARENT else 0xCC202124.toInt())
        }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), appContext.resources.displayMetrics,
    ).toInt()

    private fun statusBarHeight(): Int {
        val id = appContext.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) appContext.resources.getDimensionPixelSize(id) else dp(24)
    }

    private companion object {
        const val GRAY = 0xFF9E9E9E.toInt()
        const val AMBER = 0xFFFFB300.toInt()
        const val GREEN = 0xFF43A047.toInt()
        const val RED = 0xFFE53935.toInt()
        const val LABEL_MILLIS = 1_500L
    }
}
