package io.github.otoniel2004.yomitomo.reader.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragTrackerTest {
    private val tracker = DragTracker(slop = 30f)

    @Test
    fun `a still finger is a tap`() {
        tracker.down(100f, 100f, 0)
        tracker.move(105f, 102f)
        assertFalse(tracker.isDragging)
        assertNull(tracker.up(106f, 103f, 120))
    }

    @Test
    fun `moving past the slop is a swipe with its path and duration`() {
        tracker.down(800f, 1000f, 0)
        tracker.move(700f, 1005f)
        assertTrue(tracker.isDragging)
        tracker.move(500f, 1010f)
        val drag = tracker.up(300f, 1012f, 180)!!
        assertEquals(800f to 1000f, drag.points.first())
        assertEquals(300f to 1012f, drag.points.last())
        assertEquals(180L, drag.durationMillis)
    }

    @Test
    fun `tiny jitter does not bloat the path`() {
        tracker.down(0f, 0f, 0)
        repeat(50) { tracker.move(40f + it * 0.1f, 0f) }
        val drag = tracker.up(45f, 0f, 100)!!
        assertTrue(drag.points.size < 10)
    }

    @Test
    fun `tracker resets between gestures`() {
        tracker.down(0f, 0f, 0)
        tracker.move(100f, 0f)
        tracker.up(100f, 0f, 50)
        tracker.down(500f, 500f, 1000)
        assertNull(tracker.up(500f, 500f, 1050))
    }
}
