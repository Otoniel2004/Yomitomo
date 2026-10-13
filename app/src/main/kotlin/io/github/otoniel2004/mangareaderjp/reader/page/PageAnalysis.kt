package io.github.otoniel2004.mangareaderjp.reader.page

import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.dictionary.DictEntry
import io.github.otoniel2004.mangareaderjp.dictionary.Dictionary
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseAnalyzer
import io.github.otoniel2004.mangareaderjp.japanese.Morpheme
import io.github.otoniel2004.mangareaderjp.japanese.rubyParts
import io.github.otoniel2004.mangareaderjp.reader.ocr.Box
import io.github.otoniel2004.mangareaderjp.reader.ocr.OcrBlock
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** A character's position on screen, and whether its line is vertical. */
data class PlacedChar(val box: Box, val line: Int, val vertical: Boolean)

/**
 * A word the user can tap: one or more tokens ([start] until [end]) looked up together.
 * Verbs/adjectives absorb their endings (食べ|なかっ|た = one word), and multi-token
 * dictionary entries stay whole (仕方|が|ない = 仕方がない).
 */
data class PageWord(val start: Int, val end: Int, val segments: List<Segment>, val entry: DictEntry?) {
    val type: ItemType get() = if (entry?.isExpression == true) ItemType.EXPRESSION else ItemType.WORD
    val text: String get() = segments.joinToString("") { it.surface }
}

/** A piece of furigana to draw: [box] is where it goes on screen; vertical text stacks top to bottom. */
data class RubyMark(val text: String, val box: Box, val vertical: Boolean)

/**
 * Everything precomputed for one text block, so a tap answers instantly:
 *   segments  – Kuromoji tokens (furigana, romaji, particles)
 *   words     – tappable words with their dictionary entry
 *   rubies    – where to draw furigana
 */
class AnalyzedBlock(
    val block: OcrBlock,
    val segments: List<Segment>,
    val words: List<PageWord>,
    val rubies: List<RubyMark>,
    private val chars: List<PlacedChar>,
    /** For each character, the index of the word it belongs to (-1 for punctuation). */
    private val charWord: IntArray,
) {
    /** The word under a point (capture pixels): the character hit, or the nearest one in the block. */
    fun wordAt(x: Int, y: Int): PageWord? {
        if (chars.isEmpty()) return words.firstOrNull()
        var best = -1
        var bestDistance = Double.MAX_VALUE
        chars.forEachIndexed { i, c ->
            if (charWord[i] < 0) return@forEachIndexed
            val distance = if (c.box.expandedBy(TAP_SLOP).contains(x, y)) 0.0
            else hypot((c.box.left + c.box.right) / 2.0 - x, (c.box.top + c.box.bottom) / 2.0 - y)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return if (best < 0) null else words[charWord[best]]
    }

    private companion object {
        const val TAP_SLOP = 6
    }
}

/**
 * Turns OCR blocks into [AnalyzedBlock]s:
 *
 *   block text ──Kuromoji──► tokens ──group + dictionary──► words
 *        │                      │
 *        └─ per-char boxes ─────┴──► each token knows its characters on screen
 *                                      ├─► furigana positions (beside the kanji/katakana)
 *                                      └─► tap → character → word
 */
class PageAnalyzer(private val analyzer: JapaneseAnalyzer, private val dictionary: Dictionary) {

    /** [rubyRatio]: furigana size relative to the characters (the "Furigana size" setting). */
    fun analyze(blocks: List<OcrBlock>, rubyRatio: Double = DEFAULT_RUBY_RATIO): List<AnalyzedBlock> =
        blocks.map { analyzeBlock(it, rubyRatio) }

    private fun analyzeBlock(block: OcrBlock, rubyRatio: Double): AnalyzedBlock {
        val morphemes = analyzer.analyze(block.text)
        val segments = morphemes.map { it.segment }
        val words = groupWords(morphemes)

        val chars = block.lines.flatMapIndexed { lineIndex, line ->
            line.chars.map { PlacedChar(it.box, lineIndex, line.box.isVertical) }
        }
        // Each UTF-16 unit of the text → its character (rare kanji take two units).
        val unitToChar = block.lines.flatMap { it.chars }.flatMapIndexed { i, c -> List(c.text.length) { i } }
        val aligned = chars.isNotEmpty() && unitToChar.size == block.text.length &&
            segments.joinToString("") { it.surface } == block.text
        if (!aligned) {
            return AnalyzedBlock(block, segments, words, emptyList(), emptyList(), IntArray(0))
        }

        val tokenStart = IntArray(segments.size)
        var offset = 0
        segments.forEachIndexed { i, s -> tokenStart[i] = offset; offset += s.surface.length }

        val charWord = IntArray(chars.size) { -1 }
        words.forEachIndexed { w, word ->
            for (t in word.start until word.end) {
                for (unit in tokenStart[t] until tokenStart[t] + segments[t].surface.length) charWord[unitToChar[unit]] = w
            }
        }

        val rubies = if (block.printedFurigana) emptyList() else buildList {
            segments.forEachIndexed { t, segment ->
                var unit = tokenStart[t]
                for (part in rubyParts(segment.surface, segment.reading)) {
                    val partChars = (unit until unit + part.text.length).map { unitToChar[it] }.distinct()
                    unit += part.text.length
                    val ruby = part.ruby ?: continue
                    rubyMark(ruby, partChars.map { chars[it] }, rubyRatio)?.let(::add)
                }
            }
        }
        return AnalyzedBlock(block, segments, words, rubies, chars, charWord)
    }

    /**
     * Words a tap can select:
     *   1. the longest run of 2–4 tokens that is itself a dictionary entry (仕方がない)
     *   2. otherwise a token plus the endings glued to it (食べ + なかっ + た), looked up by its base form
     */
    private fun groupWords(morphemes: List<Morpheme>): List<PageWord> {
        val words = mutableListOf<PageWord>()
        var i = 0
        while (i < morphemes.size) {
            val head = morphemes[i]
            if (head.segment.pos == PartOfSpeech.PUNCTUATION) {
                i++
                continue
            }
            val compound = (minOf(MAX_COMPOUND, morphemes.size - i) downTo 2).firstNotNullOfOrNull { length ->
                val run = morphemes.subList(i, i + length)
                if (run.any { it.segment.pos == PartOfSpeech.PUNCTUATION }) return@firstNotNullOfOrNull null
                dictionary.byText(run.joinToString("") { it.segment.surface }, 1).firstOrNull()?.let { length to it }
            }
            if (compound != null) {
                val (length, entry) = compound
                words += PageWord(i, i + length, morphemes.subList(i, i + length).map { it.segment }, entry)
                i += length
                continue
            }

            var end = i + 1
            if (head.segment.pos in INFLECTING) {
                while (end < morphemes.size && isEnding(morphemes[end].segment)) end++
            }
            words += PageWord(i, end, morphemes.subList(i, end).map { it.segment }, lookUp(head))
            i = end
        }
        return words
    }

    private fun lookUp(morpheme: Morpheme): DictEntry? {
        val surface = morpheme.segment.surface
        return morpheme.baseForm?.let { dictionary.byText(it, 1).firstOrNull() }
            ?: dictionary.byText(surface, 1).firstOrNull()
            ?: morpheme.segment.reading?.let { dictionary.byReading(it, 1).firstOrNull() }
    }

    private fun isEnding(segment: Segment) =
        segment.pos == PartOfSpeech.AUXILIARY || segment.pos == PartOfSpeech.SUFFIX ||
            (segment.pos == PartOfSpeech.PARTICLE && segment.surface in TE_PARTICLES)

    private companion object {
        const val MAX_COMPOUND = 4
        val INFLECTING = setOf(PartOfSpeech.VERB, PartOfSpeech.ADJECTIVE, PartOfSpeech.AUXILIARY)
        val TE_PARTICLES = setOf("て", "で", "ちゃ", "じゃ", "ば")
    }
}

/**
 * Where a reading goes: above horizontal text, to the right of vertical text (as in printed
 * Japanese). Size = [ratio] of the characters (45% by default). Uses the characters of the first line the word
 * touches when it wraps.
 */
internal fun rubyMark(text: String, chars: List<PlacedChar>, ratio: Double = DEFAULT_RUBY_RATIO): RubyMark? {
    if (chars.isEmpty() || text.isEmpty()) return null
    val line = chars.first().line
    val area = chars.filter { it.line == line }.map { it.box }.reduce(Box::union)
    val vertical = chars.first().vertical
    val gap = 1
    return if (vertical) {
        val size = max(MIN_RUBY_SIZE, (area.width * ratio).roundToInt())
        val length = size * text.length
        val top = (area.top + area.bottom) / 2 - length / 2
        RubyMark(text, Box(area.right + gap, top, area.right + gap + size, top + length), vertical = true)
    } else {
        val size = max(MIN_RUBY_SIZE, (area.height * ratio).roundToInt())
        val length = size * text.length
        val left = (area.left + area.right) / 2 - length / 2
        RubyMark(text, Box(left, area.top - gap - size, left + length, area.top - gap), vertical = false)
    }
}

const val DEFAULT_RUBY_RATIO = 0.45
private const val MIN_RUBY_SIZE = 9
