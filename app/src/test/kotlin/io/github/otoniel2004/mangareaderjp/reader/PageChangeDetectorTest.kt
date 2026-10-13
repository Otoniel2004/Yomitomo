package io.github.otoniel2004.mangareaderjp.reader

import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.MOVING
import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.NEW_PAGE
import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.NONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChangeDetectorTest {
    private fun frame(value: Int, size: Int = 100) = IntArray(size) { value }

    /** A frame where only `changed` cells differ, to simulate small on-screen noise. */
    private fun noisy(base: Int, changed: Int, size: Int = 100) =
        IntArray(size) { if (it < changed) base + 100 else base }

    @Test
    fun `first page is reported once the screen settles`() {
        val detector = PageChangeDetector(settleMillis = 300)
        assertEquals(NONE, detector.onFrame(frame(200), 0))
        assertEquals(NONE, detector.onFrame(frame(200), 250))
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))
        assertEquals(NONE, detector.onFrame(frame(200), 750))
    }

    @Test
    fun `page turn is moving and then a new page`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))

        assertEquals(MOVING, detector.onFrame(frame(120), 750))   // animation frame
        assertEquals(MOVING, detector.onFrame(frame(60), 1000))   // landed on the new page
        assertEquals(NONE, detector.onFrame(frame(60), 1250))     // settling (250 ms < 300 ms)
        assertEquals(NEW_PAGE, detector.onFrame(frame(60), 1500))
    }

    @Test
    fun `small noise like a clock tick is not a new page`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))
        // 1% of the cells change a lot → mean difference of 1, below both thresholds
        assertEquals(NONE, detector.onFrame(noisy(200, changed = 1), 750))
        assertEquals(NONE, detector.onFrame(noisy(200, changed = 1), 1500))
    }

    @Test
    fun `returning to the same page after moving is not reported again`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))
        assertEquals(MOVING, detector.onFrame(frame(100), 750))
        assertEquals(MOVING, detector.onFrame(frame(200), 1000))
        assertEquals(NONE, detector.onFrame(frame(200), 1500))
    }

    @Test
    fun `black frames are flagged as blocked capture`() {
        assertTrue(PageChangeDetector.looksBlack(frame(0)))
        assertFalse(PageChangeDetector.looksBlack(noisy(0, changed = 3)))
    }
}
