package io.github.otoniel2004.mangareaderjp.reader

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import io.github.otoniel2004.mangareaderjp.reader.overlay.Drag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optional accessibility service with ONE job: replay a swipe that started on a text bubble,
 * so the manga app underneath turns the page.
 *
 *   swipe on bubble ──► our invisible touch target catches it
 *                   ──► targets become pass-through
 *                   ──► this service performs the same swipe (dispatchGesture) on the app below
 *                   ──► targets catch taps again
 *
 * It never reads screen content: canRetrieveWindowContent is off and it only listens to events
 * from this app (see res/xml/gesture_forwarder.xml). The user turns it on in Android's
 * Accessibility settings; without it, swipes that start on a bubble just don't turn the page.
 */
class GestureForwarderService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        connected.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        connected.value = false
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Performs [drag]; [onDone] runs when the gesture ends (or fails). */
    fun replay(drag: Drag, onDone: () -> Unit): Boolean {
        val (startX, startY) = drag.points.first()
        val path = Path().apply {
            moveTo(startX, startY)
            drag.points.drop(1).forEach { (x, y) -> lineTo(x, y) }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, drag.durationMillis.coerceIn(1, MAX_DURATION))
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
        }
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback, null)
    }

    companion object {
        private const val MAX_DURATION = 2_000L

        @Volatile var instance: GestureForwarderService? = null
            private set

        private val connected = MutableStateFlow(false)

        /** Whether the user has the service turned on (for the Settings screen). */
        val isConnected: StateFlow<Boolean> = connected.asStateFlow()
    }
}
