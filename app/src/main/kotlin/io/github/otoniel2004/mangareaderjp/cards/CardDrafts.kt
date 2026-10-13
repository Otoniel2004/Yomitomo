package io.github.otoniel2004.mangareaderjp.cards

import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.dictionary.Dictionary
import io.github.otoniel2004.mangareaderjp.japanese.Chunk
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseAnalyzer
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseText

/** Everything needed to show and save a card, before it is saved. */
data class CardDraft(
    val segments: List<Segment>,
    val type: ItemType,
    val meanings: List<String>,
) {
    val text: String get() = segments.joinToString("") { it.surface }
}

/**
 * Builds cards the same way no matter where the text came from, so manual cards look
 * exactly like the ones the reader saves:
 *
 *   romaji  → KanaKanjiConverter → chunks ──► fromChunks
 *   Japanese (typed, camera, screen OCR)  ──► fromJapanese (Kuromoji + JMdict)
 *
 * Card type: one dictionary word → WORD, a dictionary expression → EXPRESSION, anything else → SENTENCE.
 */
class CardDraftBuilder(private val dictionary: Dictionary, private val analyzer: JapaneseAnalyzer) {

    fun fromChunks(chunks: List<Chunk>): CardDraft {
        val segments = chunks.map { chunk ->
            val surface = chunk.choice.surface
            val reading = when {
                !JapaneseText.needsFurigana(surface) -> null
                chunk.kana.any(JapaneseText::isKanji) -> null              // kanji typed directly: reading unknown
                !surface.any(JapaneseText::isKanji) -> JapaneseText.toHiragana(surface) // katakana reads as written
                else -> chunk.hiragana                                     // converted kanji reads as typed
            }
            Segment(surface, reading, chunk.choice.pos)
        }
        val content = chunks.filter { it.choice.pos != PartOfSpeech.PUNCTUATION }
        val single = content.singleOrNull()?.choice
        return when {
            single == null -> CardDraft(segments, ItemType.SENTENCE, emptyList())
            single.isExpression -> CardDraft(segments, ItemType.EXPRESSION, single.meanings)
            else -> CardDraft(segments, ItemType.WORD, single.meanings)
        }
    }

    fun fromJapanese(text: String): CardDraft {
        val morphemes = analyzer.analyze(text.trim())
        val segments = morphemes.map { it.segment }
        val core = segments.filter { it.pos != PartOfSpeech.PUNCTUATION }.joinToString("") { it.surface }

        dictionary.byText(core, 1).firstOrNull()?.let { entry ->
            val type = if (entry.isExpression) ItemType.EXPRESSION else ItemType.WORD
            return CardDraft(segments, type, entry.meanings())
        }

        // A single content word plus endings (食べなかった = 食べ + なかっ + た) is still a word.
        val contentWords = morphemes.filter { it.segment.pos !in GRAMMAR }
        if (contentWords.size == 1) {
            val base = contentWords.first().baseForm ?: contentWords.first().segment.surface
            dictionary.byText(base, 1).firstOrNull()?.let { return CardDraft(segments, ItemType.WORD, it.meanings()) }
        }
        return CardDraft(segments, ItemType.SENTENCE, emptyList())
    }

    private companion object {
        val GRAMMAR = setOf(PartOfSpeech.AUXILIARY, PartOfSpeech.PARTICLE, PartOfSpeech.SUFFIX, PartOfSpeech.PUNCTUATION)
    }
}
