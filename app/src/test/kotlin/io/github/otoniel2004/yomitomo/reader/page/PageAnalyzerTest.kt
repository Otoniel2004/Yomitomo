package io.github.otoniel2004.yomitomo.reader.page

import io.github.otoniel2004.yomitomo.data.ItemType
import io.github.otoniel2004.yomitomo.japanese.FakeDictionary
import io.github.otoniel2004.yomitomo.japanese.JapaneseAnalyzer
import io.github.otoniel2004.yomitomo.reader.ocr.Box
import io.github.otoniel2004.yomitomo.reader.ocr.OcrBlock
import io.github.otoniel2004.yomitomo.reader.ocr.OcrChar
import io.github.otoniel2004.yomitomo.reader.ocr.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnalyzerTest {
    private val pages = PageAnalyzer(analyzer, FakeDictionary())

    companion object {
        private val analyzer = JapaneseAnalyzer() // slow to build: share it across tests
        private const val SIZE = 40
    }

    /** One horizontal line: characters side by side starting at (100, 500). */
    private fun horizontal(text: String): OcrBlock {
        val chars = text.mapIndexed { i, c -> OcrChar(c.toString(), Box(100 + i * SIZE, 500, 140 + i * SIZE, 540)) }
        val line = OcrLine(text, chars.map { it.box }.reduce(Box::union), chars)
        return OcrBlock(text, line.box, listOf(line))
    }

    /** One vertical column: characters stacked starting at (500, 100). */
    private fun vertical(text: String): OcrBlock {
        val chars = text.mapIndexed { i, c -> OcrChar(c.toString(), Box(500, 100 + i * SIZE, 540, 140 + i * SIZE)) }
        val line = OcrLine(text, chars.map { it.box }.reduce(Box::union), chars)
        return OcrBlock(text, line.box, listOf(line))
    }

    @Test
    fun `verbs absorb their endings into one word`() {
        val block = pages.analyze(listOf(horizontal("俺は絶対に諦めない"))).single()
        assertEquals(listOf("俺", "は", "絶対", "に", "諦めない"), block.words.map { it.text })
        assertEquals(listOf("to give up"), block.words.last().entry?.meanings())
    }

    @Test
    fun `furigana sits above horizontal kanji only`() {
        val rubies = pages.analyze(listOf(horizontal("俺は絶対に諦めない"))).single().rubies
        assertEquals(listOf("おれ", "ぜったい", "あきら"), rubies.map { it.text })
        val ore = rubies.first()
        assertEquals(499, ore.box.bottom)              // just above the line
        assertEquals(120, (ore.box.left + ore.box.right) / 2) // centered over 俺 (100..140)
        assertTrue(rubies.none { it.vertical })
    }

    @Test
    fun `each furigana mark points to its word`() {
        val block = pages.analyze(listOf(horizontal("俺は絶対に諦めない"))).single()
        assertEquals(listOf("俺", "絶対", "諦めない"), block.rubies.map { block.words[it.word].text })
    }

    @Test
    fun `furigana sits right of vertical kanji`() {
        val rubies = pages.analyze(listOf(vertical("俺は絶対に諦めない"))).single().rubies
        assertTrue(rubies.all { it.vertical && it.box.left == 541 })
    }

    @Test
    fun `tap finds the word under the finger`() {
        val block = pages.analyze(listOf(horizontal("俺は絶対に諦めない"))).single()
        assertEquals("は", block.wordAt(160, 520)?.text)        // char 1
        assertEquals("諦めない", block.wordAt(380, 520)?.text)  // char 7 (な)
        assertEquals("絶対", block.wordAt(195, 520)?.text)      // edge of char 2
    }

    @Test
    fun `book mode picks the sentence under the finger`() {
        // 猫が好き。犬も好き。 — 10 characters on one line, 40 px each, starting at x = 100.
        val block = pages.analyze(listOf(horizontal("猫が好き。犬も好き。"))).single()
        assertEquals("猫が好き。", block.sentenceAt(120, 520)!!.joinToString("") { it.surface })
        assertEquals("犬も好き。", block.sentenceAt(340, 520)!!.joinToString("") { it.surface })
    }

    @Test
    fun `sentence keeps its closing quote`() {
        val block = pages.analyze(listOf(horizontal("「行く！」と言った。"))).single()
        assertEquals("「行く！」", block.sentenceAt(140, 520)!!.joinToString("") { it.surface })
    }

    @Test
    fun `dictionary phrases stay one expression`() {
        val word = pages.analyze(listOf(horizontal("仕方がない"))).single().words.single()
        assertEquals("仕方がない", word.text)
        assertEquals(ItemType.EXPRESSION, word.type)
    }

    @Test
    fun `no furigana where the page already prints it`() {
        val block = horizontal("絶対").copy(printedFurigana = true)
        assertTrue(pages.analyze(listOf(block)).single().rubies.isEmpty())
    }
}
