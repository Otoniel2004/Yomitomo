package io.github.otoniel2004.mangareaderjp.reader

import kotlin.math.abs

/**
 * Decides when a new page is on screen, from a stream of frame "signatures"
 * (small grids of brightness values, see [FrameSampler]).
 *
 *   frame differs from the previous one?  ──► MOVING (page-turn animation, scrolling)
 *   screen still for [settleMillis]?
 *        └─ differs from the last page?  ──► NEW_PAGE
 *        └─ same page                    ──► NONE
 *
 * Differences are the mean absolute brightness difference (0..255) between two signatures.
 */
class PageChangeDetector(
    private val motionThreshold: Double = 3.0,
    private val pageThreshold: Double = 8.0,
    private val settleMillis: Long = 300,
) {
    enum class Event { NONE, MOVING, NEW_PAGE }

    private var previous: IntArray? = null
    private var page: IntArray? = null
    private var lastMotionAt = 0L

    fun onFrame(signature: IntArray, now: Long): Event {
        val prev = previous
        previous = signature
        if (prev == null) {
            lastMotionAt = now
            return Event.NONE
        }
        if (difference(prev, signature) > motionThreshold) {
            lastMotionAt = now
            return Event.MOVING
        }
        if (now - lastMotionAt < settleMillis) return Event.NONE

        val current = page
        if (current == null || difference(current, signature) > pageThreshold) {
            page = signature
            return Event.NEW_PAGE
        }
        return Event.NONE
    }

    companion object {
        fun difference(a: IntArray, b: IntArray): Double {
            require(a.size == b.size) { "Signatures must have the same size" }
            var total = 0L
            for (i in a.indices) total += abs(a[i] - b[i])
            return total.toDouble() / a.size
        }

        /** An all-black frame usually means the app on screen blocks capture (FLAG_SECURE). */
        fun looksBlack(signature: IntArray) = signature.all { it < 8 }
    }
}
