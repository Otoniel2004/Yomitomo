package io.github.otoniel2004.yomitomo.reader.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * A full-screen window over the manga app that hosts a Compose popup (word or sentence).
 * While it's open it takes every touch, so tapping outside the card closes it without
 * also turning the page.
 *
 * Compose normally lives inside an Activity, which provides a "lifecycle". A window added
 * from a Service has none, so [OverlayLifecycle] supplies a minimal one.
 */
class PopupOverlay(context: Context, private val onClosed: () -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var lifecycle: OverlayLifecycle? = null

    val isShowing: Boolean get() = view != null

    /** Shows [content]; it receives a `close` function to call when done. Main thread only. */
    fun show(content: @Composable (close: () -> Unit) -> Unit) = main.post {
        removeView()
        val owner = OverlayLifecycle().also { it.start() }
        val compose = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { content(::close) }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        windowManager.addView(compose, params)
        view = compose
        lifecycle = owner
    }

    fun close() = main.post {
        if (view == null) return@post
        removeView()
        onClosed()
    }

    private fun removeView() {
        view?.let { runCatching { windowManager.removeView(it) } }
        lifecycle?.stop()
        view = null
        lifecycle = null
    }

    private class OverlayLifecycle : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
