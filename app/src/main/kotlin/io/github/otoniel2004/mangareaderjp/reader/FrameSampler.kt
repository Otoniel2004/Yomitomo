package io.github.otoniel2004.mangareaderjp.reader

import android.media.Image
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box

/**
 * Turns a captured frame into a small brightness grid (the "signature").
 * Comparing two grids of a few thousand numbers is far cheaper than comparing full images.
 *
 * The top [IGNORED_TOP_FRACTION] of the screen is skipped: it holds the status bar
 * (clock, notifications) and our own status dot, which would look like page changes.
 */
object FrameSampler {
    const val COLUMNS = 48
    const val ROWS = 96
    private const val IGNORED_TOP_FRACTION = 0.10

    fun signature(image: Image): IntArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val out = IntArray(COLUMNS * ROWS)
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val x = sampleX(col, image.width)
                val y = sampleY(row, image.height)
                val i = y * rowStride + x * pixelStride
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                out[row * COLUMNS + col] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        return out
    }

    /**
     * Marks the grid cells that fall inside our own drawings (OCR boxes, furigana...),
     * so the page detector ignores them: we don't want to detect our own overlay as a page change.
     */
    fun cellMask(boxes: List<Box>, width: Int, height: Int, margin: Int): BooleanArray {
        val grown = boxes.map { it.expandedBy(margin) }
        return BooleanArray(COLUMNS * ROWS) { cell ->
            val x = sampleX(cell % COLUMNS, width)
            val y = sampleY(cell / COLUMNS, height)
            grown.any { it.contains(x, y) }
        }
    }

    internal fun sampleX(col: Int, width: Int) = (2 * col + 1) * width / (2 * COLUMNS)

    internal fun sampleY(row: Int, height: Int): Int {
        val top = (height * IGNORED_TOP_FRACTION).toInt()
        return top + (2 * row + 1) * (height - top) / (2 * ROWS)
    }
}
