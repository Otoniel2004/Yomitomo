package io.github.otoniel2004.mangareaderjp.reader.ocr

import io.github.otoniel2004.mangareaderjp.japanese.JapaneseText

/**
 * Cleans raw OCR output before it is used:
 *
 *   1. remove spaces (the OCR sometimes puts spaces between Japanese characters)
 *   2. drop lines without real Japanese (URLs, page numbers, English-only noise)
 *   3. drop printed furigana: hiragana-only lines much thinner than the block's other lines
 *   4. order lines for reading: vertical columns right → left, horizontal lines top → bottom
 *   5. rebuild each block's text and box from the lines that survived
 */
object OcrFilter {
    private const val MIN_JAPANESE_RATIO = 0.5
    private const val MIN_JAPANESE_CHARS = 2
    private const val FURIGANA_THICKNESS_RATIO = 0.6

    fun clean(blocks: List<OcrBlock>): List<OcrBlock> = blocks.mapNotNull(::cleanBlock)

    private fun cleanBlock(block: OcrBlock): OcrBlock? {
        val japaneseLines = block.lines
            .map { it.copy(text = it.text.filterNot(Char::isWhitespace)) }
            .filter { isMostlyJapanese(it.text) }
        if (japaneseLines.isEmpty()) return null

        val lines = dropPrintedFurigana(japaneseLines)
        val vertical = lines.count { it.box.isVertical } * 2 > lines.size
        val ordered = if (vertical) lines.sortedByDescending { it.box.right } else lines.sortedBy { it.box.top }

        return OcrBlock(
            text = ordered.joinToString("") { it.text },
            box = ordered.map { it.box }.reduce(Box::union),
            lines = ordered,
        )
    }

    fun isJapanese(c: Char) =
        JapaneseText.isKanji(c) || JapaneseText.isHiragana(c) || JapaneseText.isKatakana(c)

    /**
     * Keeps a line if it has real Japanese in it: at least [MIN_JAPANESE_CHARS] Japanese characters
     * (so mixed text like "ガンガンONLINE" survives), or mostly Japanese if it's very short.
     * URLs, page numbers and English-only lines have none and are dropped.
     */
    fun isMostlyJapanese(text: String): Boolean {
        val visible = text.filterNot(Char::isWhitespace)
        if (visible.isEmpty()) return false
        val japanese = visible.count(::isJapanese)
        return japanese >= MIN_JAPANESE_CHARS ||
            (japanese > 0 && japanese.toDouble() / visible.length >= MIN_JAPANESE_RATIO)
    }

    private fun dropPrintedFurigana(lines: List<OcrLine>): List<OcrLine> {
        if (lines.size < 2) return lines
        val median = lines.map { it.box.thickness }.sorted()[lines.size / 2]
        val kept = lines.filterNot { line ->
            line.box.thickness < median * FURIGANA_THICKNESS_RATIO &&
                line.text.all { JapaneseText.isHiragana(it) || it == 'ー' }
        }
        return kept.ifEmpty { lines }
    }
}
