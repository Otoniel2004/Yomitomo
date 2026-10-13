package io.github.otoniel2004.mangareaderjp.reader

import kotlin.math.abs

/**
 * Decides when a new page is on screen, from a stream of frame "signatures"
 * (small grids of brightness values, see [FrameSampler]).
 *
 *   frame differs from the previous one?  ──► MOVING (page-turn animation, scrolling)
 *   screen still for [settleMillis] after moving?
 *        └─ differs from the last page?  ──► NEW_PAGE
 *        └─ same page as before          ──► SAME_PAGE (e.g. a popup came and went)
 *   otherwise                            ──► NONE
 *
 * Differences are the mean absolute brightness difference (0..255) between two signatures.
 * Cells marked in `ignored` (where we draw our own overlays) don't count.
 */
class PageChangeDetector(
    private val motionThreshold: Double = 3.0,
    private val pageThreshold: Double = 8.0,
    private val settleMillis: Long = 300,
) {
    enum class Event { NONE, MOVING, NEW_PAGE, SAME_PAGE }

    private var previous: IntArray? = null
    private var page: IntArray? = null
    private var lastMotionAt = 0L
    private var movedSinceSettle = false

    fun onFrame(signature: IntArray, now: Long, ignored: BooleanArray? = null): Event {
        val prev = previous
        previous = signature
        if (prev == null) {
            lastMotionAt = now
            movedSinceSettle = true
            return Event.NONE
        }
        if (difference(prev, signature, ignored) > motionThreshold) {
            lastMotionAt = now
            movedSinceSettle = true
            return Event.MOVING
        }
        if (now - lastMotionAt < settleMillis || !movedSinceSettle) return Event.NONE

        movedSinceSettle = false
        val current = page
        if (current == null || difference(current, signature, ignored) > pageThreshold) {
            page = signature
            return Event.NEW_PAGE
        }
        return Event.SAME_PAGE
    }

    /**
     * Forget the last frame (e.g. after our own popup covered the screen). The next frames
     * settle again and are compared with the remembered page, giving SAME_PAGE if nothing changed.
     */
    fun forgetPrevious() {
        previous = null
    }

    companion object {
        fun difference(a: IntArray, b: IntArray, ignored: BooleanArray? = null): Double {
            require(a.size == b.size) { "Signatures must have the same size" }
            var total = 0L
            var counted = 0
            for (i in a.indices) {
                if (ignored != null && ignored[i]) continue
                total += abs(a[i] - b[i])
                counted++
            }
            return if (counted == 0) 0.0 else total.toDouble() / counted
        }

        /** An all-black frame usually means the app on screen blocks capture (FLAG_SECURE). */
        fun looksBlack(signature: IntArray) = signature.all { it < 8 }
    }
}
