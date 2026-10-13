package io.github.otoniel2004.mangareaderjp.reader.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFilterTest {
    /** A vertical column: narrow and tall, its right edge at `right`. */
    private fun column(text: String, right: Int, width: Int = 30) =
        OcrLine(text, Box(right - width, 100, right, 100 + text.length * width))

    private fun row(text: String, top: Int, height: Int = 30) =
        OcrLine(text, Box(100, top, 100 + text.length * height, top + height))

    private fun block(vararg lines: OcrLine) =
        OcrBlock(lines.joinToString("") { it.text }, lines.map { it.box }.reduce(Box::union), lines.toList())

    @Test
    fun `japanese detection`() {
        assertTrue(OcrFilter.isMostlyJapanese("何だと！？"))
        assertTrue(OcrFilter.isMostlyJapanese("コーヒー2杯"))
        assertTrue(OcrFilter.isMostlyJapanese("ガンガンONLINE")) // mixed text is kept
        assertFalse(OcrFilter.isMostlyJapanese("https://www.ganganonline.com"))
        assertFalse(OcrFilter.isMostlyJapanese("Chapter 12"))
        assertFalse(OcrFilter.isMostlyJapanese("…！？"))
        assertFalse(OcrFilter.isMostlyJapanese("   "))
    }

    @Test
    fun `vertical columns are read right to left`() {
        val result = OcrFilter.clean(listOf(block(column("ない", right = 100), column("仕方が", right = 200))))
        assertEquals("仕方がない", result.single().text)
    }

    @Test
    fun `horizontal lines are read top to bottom`() {
        val result = OcrFilter.clean(listOf(block(row("ですね", top = 200), row("いい天気", top = 100))))
        assertEquals("いい天気ですね", result.single().text)
    }

    @Test
    fun `spaces are removed and latin noise is dropped`() {
        val result = OcrFilter.clean(
            listOf(
                block(row("約 束 だ", top = 100)),
                block(row("p.42", top = 900)),
            ),
        )
        assertEquals(listOf("約束だ"), result.map { it.text })
    }

    @Test
    fun `thin hiragana-only line next to kanji is treated as printed furigana`() {
        val main = column("絶対", right = 200, width = 40)
        val ruby = column("ぜったい", right = 220, width = 18)
        val result = OcrFilter.clean(listOf(block(main, ruby, column("諦めない", right = 150, width = 40))))
        assertEquals("絶対諦めない", result.single().text)
    }

    @Test
    fun `block box is rebuilt from the kept lines`() {
        val kept = row("こんにちは", top = 100)
        val result = OcrFilter.clean(listOf(block(kept, row("ABC", top = 500))))
        assertEquals(kept.box, result.single().box)
    }
}
