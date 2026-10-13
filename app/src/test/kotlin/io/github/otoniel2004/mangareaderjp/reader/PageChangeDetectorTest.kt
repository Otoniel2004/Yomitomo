package io.github.otoniel2004.mangareaderjp.reader

import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.MOVING
import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.NEW_PAGE
import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.NONE
import io.github.otoniel2004.mangareaderjp.reader.PageChangeDetector.Event.SAME_PAGE
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageChangeDetectorTest {
    private fun frame(value: Int, size: Int = 100) = IntArray(size) { value }

    /** A frame where only the first `changed` cells differ, to simulate small on-screen changes. */
    private fun partly(base: Int, changed: Int, size: Int = 100) =
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
    fun `small noise like a clock tick is ignored`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))
        // 1% of the cells change a lot → mean difference of 1, below both thresholds
        assertEquals(NONE, detector.onFrame(partly(200, changed = 1), 750))
        assertEquals(NONE, detector.onFrame(partly(200, changed = 1), 1500))
    }

    @Test
    fun `settling back on the same page reports same page once`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))
        assertEquals(MOVING, detector.onFrame(frame(100), 750))
        assertEquals(MOVING, detector.onFrame(frame(200), 1000))
        assertEquals(SAME_PAGE, detector.onFrame(frame(200), 1500))
        assertEquals(NONE, detector.onFrame(frame(200), 1750))
    }

    @Test
    fun `our own drawings in ignored cells do not count as changes`() {
        val detector = PageChangeDetector(settleMillis = 300)
        detector.onFrame(frame(200), 0)
        assertEquals(NEW_PAGE, detector.onFrame(frame(200), 500))

        // Boxes appear over 30% of the screen, but those cells are masked.
        val mask = BooleanArray(100) { it < 30 }
        assertEquals(NONE, detector.onFrame(partly(200, changed = 30), 750, mask))
        assertEquals(NONE, detector.onFrame(partly(200, changed = 30), 1500, mask))

        // Without the mask the same drawing would look like motion.
        val unmasked = PageChangeDetector(settleMillis = 300)
        unmasked.onFrame(frame(200), 0)
        assertEquals(MOVING, unmasked.onFrame(partly(200, changed = 30), 250))
    }

    @Test
    fun `black frames are flagged as blocked capture`() {
        assertTrue(PageChangeDetector.looksBlack(frame(0)))
        assertFalse(PageChangeDetector.looksBlack(partly(0, changed = 3)))
    }

    @Test
    fun `cell mask marks only cells inside the boxes`() {
        val width = 480
        val height = 960
        val mask = FrameSampler.cellMask(listOf(Box(0, 0, width, height)), width, height, margin = 0)
        assertTrue(mask.all { it })

        val none = FrameSampler.cellMask(emptyList(), width, height, margin = 0)
        assertFalse(none.any { it })

        // Left half of the screen → exactly the left half of the columns.
        val half = FrameSampler.cellMask(listOf(Box(0, 0, width / 2, height)), width, height, margin = 0)
        assertEquals(FrameSampler.COLUMNS * FrameSampler.ROWS / 2, half.count { it })
    }
}
