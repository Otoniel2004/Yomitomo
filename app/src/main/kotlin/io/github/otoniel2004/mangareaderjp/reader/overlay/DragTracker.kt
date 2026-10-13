package io.github.otoniel2004.mangareaderjp.reader.overlay

import kotlin.math.hypot

/** A finger movement to replay on the app underneath: screen points and how long it took. */
data class Drag(val points: List<Pair<Float, Float>>, val durationMillis: Long)

/**
 * Tells a tap from a swipe on a text bubble (the FrankYomik rule):
 *
 *   finger moves more than [slop] px before lifting → swipe (recorded, to be replayed)
 *   otherwise                                      → tap (handled as 1 or 2 taps)
 *
 * Feed it every touch event of one gesture: [down], [move]s, then [up].
 */
class DragTracker(private val slop: Float) {
    private val points = mutableListOf<Pair<Float, Float>>()
    private var startTime = 0L

    var isDragging = false
        private set

    fun down(x: Float, y: Float, time: Long) {
        points.clear()
        points += x to y
        startTime = time
        isDragging = false
    }

    fun move(x: Float, y: Float) {
        if (points.isEmpty()) return
        val (startX, startY) = points.first()
        if (!isDragging && hypot(x - startX, y - startY) > slop) isDragging = true
        // Keep the path light: only points a few px apart.
        val (lastX, lastY) = points.last()
        if (hypot(x - lastX, y - lastY) >= MIN_STEP) points += x to y
    }

    /** Ends the gesture: the drag to replay, or null if it was a tap. */
    fun up(x: Float, y: Float, time: Long): Drag? {
        if (points.isEmpty()) return null
        move(x, y)
        if (points.last() != (x to y)) points += x to y
        val drag = if (isDragging) Drag(points.toList(), (time - startTime).coerceAtLeast(1)) else null
        points.clear()
        isDragging = false
        return drag
    }

    private companion object {
        const val MIN_STEP = 4f
    }
}
