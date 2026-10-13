package io.github.otoniel2004.mangareaderjp.reader

import android.media.Image

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
        val width = image.width
        val height = image.height
        val top = (height * IGNORED_TOP_FRACTION).toInt()
        val usable = height - top

        val out = IntArray(COLUMNS * ROWS)
        for (row in 0 until ROWS) {
            val y = top + (2 * row + 1) * usable / (2 * ROWS)
            for (col in 0 until COLUMNS) {
                val x = (2 * col + 1) * width / (2 * COLUMNS)
                val i = y * rowStride + x * pixelStride
                val r = buffer.get(i).toInt() and 0xFF
                val g = buffer.get(i + 1).toInt() and 0xFF
                val b = buffer.get(i + 2).toInt() and 0xFF
                out[row * COLUMNS + col] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        return out
    }
}
