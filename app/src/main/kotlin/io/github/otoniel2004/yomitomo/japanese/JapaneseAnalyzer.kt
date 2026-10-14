package io.github.otoniel2004.yomitomo.japanese

import com.atilika.kuromoji.ipadic.Tokenizer
import io.github.otoniel2004.yomitomo.data.PartOfSpeech
import io.github.otoniel2004.yomitomo.data.Segment

/** A token plus its dictionary form (食べ → 食べる), used for dictionary lookups. */
data class Morpheme(val segment: Segment, val baseForm: String?)

/**
 * Splits Japanese text into words with Kuromoji (a morphological analyzer with the IPADIC
 * dictionary, fully offline). For each word it gives the reading, the part of speech and the
 * dictionary form:
 *
 *   俺は絶対に諦めない → 俺(pronoun, おれ) は(particle) 絶対(adverb, ぜったい) に(particle)
 *                         諦め(verb, あきらめ, base 諦める) ない(auxiliary)
 *
 * Building the tokenizer takes a second or two, so it is created lazily; call [warmUp]
 * off the main thread early.
 */
class JapaneseAnalyzer {
    private val tokenizer by lazy { Tokenizer() }

    fun warmUp() {
        tokenizer
    }

    fun analyze(text: String): List<Morpheme> = tokenizer.tokenize(text).map { token ->
        val surface = token.surface
        // Words the tokenizer doesn't know (names like コミックシーモア) come without a reading;
        // pure katakana is read as written.
        val reading = token.reading.takeIf { it != "*" }?.let(JapaneseText::toHiragana)
            ?: surface.takeIf { s -> s.all { JapaneseText.isKatakana(it) } }?.let(JapaneseText::toHiragana)
        // IPADIC tags some full-width symbols (！ ？) as nouns: punctuation is punctuation.
        val pos = if (isPunctuation(surface)) PartOfSpeech.PUNCTUATION
        else partOfSpeech(token.partOfSpeechLevel1, token.partOfSpeechLevel2)
        Morpheme(
            segment = Segment(
                surface = surface,
                reading = reading?.takeIf { JapaneseText.needsFurigana(surface) },
                pos = pos,
            ),
            baseForm = token.baseForm.takeIf { it != "*" },
        )
    }

    fun segments(text: String): List<Segment> = analyze(text).map { it.segment }

    companion object {
        /** Text made only of symbols: ！？。、「」…〜・ and their ASCII versions. */
        fun isPunctuation(surface: String) = surface.isNotEmpty() && surface.all { c ->
            !c.isLetterOrDigit() && !JapaneseText.isKanji(c) && !JapaneseText.isHiragana(c) &&
                !JapaneseText.isKatakana(c)
        }

        /** IPADIC part-of-speech names (Japanese) → our simplified classes. */
        fun partOfSpeech(level1: String, level2: String): PartOfSpeech = when (level1) {
            "名詞" -> when (level2) {
                "代名詞" -> PartOfSpeech.PRONOUN
                "接尾" -> PartOfSpeech.SUFFIX
                "形容動詞語幹" -> PartOfSpeech.ADJECTIVE
                else -> PartOfSpeech.NOUN
            }
            "動詞" -> if (level2 == "非自立" || level2 == "接尾") PartOfSpeech.AUXILIARY else PartOfSpeech.VERB
            "形容詞" -> if (level2 == "非自立" || level2 == "接尾") PartOfSpeech.AUXILIARY else PartOfSpeech.ADJECTIVE
            "副詞" -> PartOfSpeech.ADVERB
            "助詞" -> PartOfSpeech.PARTICLE
            "助動詞" -> PartOfSpeech.AUXILIARY
            "接続詞" -> PartOfSpeech.CONJUNCTION
            "感動詞", "フィラー" -> PartOfSpeech.INTERJECTION
            "接頭詞" -> PartOfSpeech.PREFIX
            "記号" -> PartOfSpeech.PUNCTUATION
            else -> PartOfSpeech.OTHER
        }
    }
}
