package io.github.otoniel2004.yomitomo.japanese

import io.github.otoniel2004.yomitomo.data.PartOfSpeech
import io.github.otoniel2004.yomitomo.dictionary.DictEntry
import io.github.otoniel2004.yomitomo.dictionary.Dictionary

/** One way to write a chunk ("hashi" → 橋, 箸, はし, ハシ). */
data class Candidate(
    val surface: String,
    val pos: PartOfSpeech,
    val meanings: List<String> = emptyList(),
    val isExpression: Boolean = false,
    val fromDictionary: Boolean = false,
    /** JLPT level 5..1, or 0. */
    val jlpt: Int = 0,
)

/** A piece of the input with its writing options; [selected] is the one in use. */
data class Chunk(val kana: String, val candidates: List<Candidate>, val selected: Int = 0) {
    val choice: Candidate get() = candidates[selected]
    val hiragana: String get() = JapaneseText.toHiragana(kana)
}

/**
 * Romaji → Japanese with kanji, like a phone keyboard but backed by JMdict:
 *
 *   "ore wa zettai ni akiramenai"
 *     1. split on spaces            ore | wa | zettai | ni | akiramenai
 *     2. romaji → kana              おれ | は | ぜったい | に | あきらめない   (wa/o/e alone are particles)
 *     3. kana → candidates          俺… | は | 絶対… | に | 諦めない…
 *          exact reading match in the dictionary, plus conjugated forms
 *          (あきらめない → あきらめる → 諦める → 諦めない)
 *     4. most common candidate preselected; the UI lets the user pick another
 *
 * A whole phrase that is itself a dictionary entry ("shikata ga nai" → 仕方がない) stays one chunk.
 */
class KanaKanjiConverter(private val dictionary: Dictionary) {

    private data class Word(val kana: String, val particle: String? = null, val punctuation: Boolean = false, val katakana: Boolean = false)

    fun convert(input: String): List<Chunk> {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.flatMap(::toWords)
        val content = words.filterNot { it.punctuation }

        if (content.size >= 2) {
            val phrase = content.joinToString("") { it.particle ?: it.kana }
            val phraseCandidates = candidatesFor(JapaneseText.toHiragana(phrase), katakana = false, exactOnly = true)
            if (phraseCandidates.isNotEmpty()) {
                return listOf(Chunk(phrase, phraseCandidates + plainVariants(phrase, phraseCandidates))) +
                    words.filter { it.punctuation }.map(::punctuationChunk)
            }
        }
        return words.flatMap(::chunksFor)
    }

    private fun toWords(raw: String): List<Word> {
        val particle = PARTICLES[raw.lowercase()]
        val kana = if (RomajiToKana.looksLikeRomaji(raw)) RomajiToKana.convert(raw) else raw
        val letters = raw.filter(Char::isLetter)
        val katakana = letters.length > 1 && letters.all { it.isUpperCase() && it in 'A'..'Z' }

        // Peel punctuation off both ends: "akiramenai!" → あきらめない + ！
        val body = kana.trim { it in PUNCTUATION }
        val leading = kana.takeWhile { it in PUNCTUATION }
        val trailing = kana.takeLastWhile { it in PUNCTUATION }.takeIf { body.isNotEmpty() }.orEmpty()
        return buildList {
            leading.forEach { add(Word(it.toString(), punctuation = true)) }
            if (body.isNotEmpty()) add(Word(body, particle = particle, katakana = katakana))
            trailing.forEach { add(Word(it.toString(), punctuation = true)) }
        }
    }

    private fun punctuationChunk(word: Word) = Chunk(word.kana, listOf(Candidate(word.kana, PartOfSpeech.PUNCTUATION)))

    private fun chunksFor(word: Word): List<Chunk> {
        if (word.punctuation) return listOf(punctuationChunk(word))
        if (word.particle != null) {
            val options = listOf(Candidate(word.particle, PartOfSpeech.PARTICLE)) +
                listOf(word.kana).filter { it != word.particle }.map { Candidate(it, PartOfSpeech.OTHER) }
            return listOf(Chunk(word.particle, options))
        }
        // Already contains kanji (typed with a Japanese keyboard): keep it as is.
        if (word.kana.any(JapaneseText::isKanji)) {
            val entry = dictionary.byText(word.kana, 1).firstOrNull()
            return listOf(Chunk(word.kana, listOf(candidateOf(entry, word.kana))))
        }

        val hiragana = JapaneseText.toHiragana(word.kana)
        val candidates = candidatesFor(hiragana, word.katakana, exactOnly = false)
        if (candidates.isEmpty()) splitTrailingParticle(hiragana)?.let { return it }
        return listOf(Chunk(word.kana, candidates + plainVariants(word.kana, candidates)))
    }

    /** "orewa" → おれ + は, when おれ is a real word and the rest is a particle. */
    private fun splitTrailingParticle(hiragana: String): List<Chunk>? {
        for ((ending, particle) in ATTACHED_PARTICLES) {
            if (!hiragana.endsWith(ending) || hiragana.length <= ending.length) continue
            val head = hiragana.dropLast(ending.length)
            val headCandidates = candidatesFor(head, katakana = false, exactOnly = false)
            if (headCandidates.isNotEmpty()) {
                return listOf(
                    Chunk(head, headCandidates + plainVariants(head, headCandidates)),
                    Chunk(particle, listOf(Candidate(particle, PartOfSpeech.PARTICLE))),
                )
            }
        }
        return null
    }

    private data class Ranked(val candidate: Candidate, val priority: Int, val steps: Int)

    /** Dictionary-backed ways of writing [hiragana], most common first. */
    private fun candidatesFor(hiragana: String, katakana: Boolean, exactOnly: Boolean): List<Candidate> {
        val ranked = mutableListOf<Ranked>()

        // Typed romaji spells long vowels out (koohii → こおひい); dictionaries write ー (コーヒー).
        for (key in listOf(hiragana, longVowelVariant(hiragana)).distinct()) {
            for (entry in dictionary.byReading(key, 10)) {
                val readingForm = entry.readings.firstOrNull { JapaneseText.toHiragana(it) == key } ?: key
                val forms = if (entry.usuallyKana || entry.kanji.isEmpty()) listOf(readingForm) + entry.kanji.take(1)
                else entry.kanji.take(2) + readingForm
                // An entry's main form ranks by its frequency; its alternative spellings come after
                // the main forms of all other entries (橋, 箸, then はし).
                forms.forEachIndexed { index, form ->
                    ranked += Ranked(candidateOf(entry, form), entry.priority + if (index == 0) 0 else ALTERNATIVE_PENALTY, 0)
                }
            }
        }

        if (!exactOnly) {
            for (result in Deinflector.deinflect(hiragana)) {
                val entries = dictionary.byReading(result.base, 5)
                    .filter { entry -> result.wordClass.matches(entry.senses.flatMap { it.pos }) }
                for (entry in entries) {
                    val surface = when {
                        entry.usuallyKana || entry.kanji.isEmpty() -> hiragana
                        result.wordClass == Deinflector.WordClass.KURU -> "来" + hiragana.drop(1)
                        else -> reinflect(entry.kanji.first(), result.base, hiragana)
                    }
                    ranked += Ranked(candidateOf(entry, surface), entry.priority, result.steps)
                }
            }
        }

        val sorted = ranked.sortedWith(compareBy({ it.priority }, { it.steps })).map { it.candidate }
        val withKatakana = if (katakana) {
            listOf(Candidate(RomajiToKana.toKatakana(hiragana), sorted.firstOrNull()?.pos ?: PartOfSpeech.NOUN,
                sorted.firstOrNull()?.meanings.orEmpty(), fromDictionary = sorted.isNotEmpty())) + sorted
        } else sorted
        return withKatakana.distinctBy { it.surface }.take(MAX_CANDIDATES)
    }

    /** Plain hiragana and katakana are always offered, after the dictionary options. */
    private fun plainVariants(kana: String, existing: List<Candidate>): List<Candidate> {
        val hiragana = JapaneseText.toHiragana(kana)
        val pos = existing.firstOrNull()?.pos ?: PartOfSpeech.OTHER
        return listOf(hiragana, RomajiToKana.toKatakana(hiragana))
            .filter { form -> existing.none { it.surface == form } }
            .distinct()
            .map { Candidate(it, pos) }
    }

    private fun candidateOf(entry: DictEntry?, surface: String) = Candidate(
        surface = surface,
        pos = entry?.partOfSpeech ?: PartOfSpeech.OTHER,
        meanings = entry?.meanings().orEmpty(),
        isExpression = entry?.isExpression == true,
        fromDictionary = entry != null,
        jlpt = entry?.jlpt ?: 0,
    )

    companion object {
        private const val MAX_CANDIDATES = 8
        private const val ALTERNATIVE_PENALTY = 1_000

        /**
         * Replaces a vowel that repeats the previous syllable's vowel with ー:
         *   こおひい → こーひー, けえき → けーき.  Only used as an extra lookup key.
         */
        fun longVowelVariant(hiragana: String): String = buildString {
            for ((i, c) in hiragana.withIndex()) {
                val previous = hiragana.getOrNull(i - 1)
                val vowel = VOWEL_KANA.indexOf(c)
                val previousVowel = previous?.let { Romaji.fromKana(it.toString()).lastOrNull() }
                append(if (vowel >= 0 && previousVowel == "aiueo"[vowel]) 'ー' else c)
            }
        }

        private const val VOWEL_KANA = "あいうえお"
        private const val PUNCTUATION = "。、！？!?.,"

        /** Romaji words that are particles when written on their own. */
        private val PARTICLES = mapOf(
            "wa" to "は", "ga" to "が", "wo" to "を", "o" to "を", "ni" to "に", "de" to "で",
            "to" to "と", "mo" to "も", "no" to "の", "e" to "へ", "ka" to "か", "ne" to "ね",
            "yo" to "よ", "ya" to "や", "kara" to "から", "made" to "まで", "yori" to "より",
            "dake" to "だけ", "shika" to "しか", "sae" to "さえ", "zo" to "ぞ", "ze" to "ぜ",
        )

        /** Particles that may be typed glued to the previous word (longest first). */
        private val ATTACHED_PARTICLES = listOf(
            "から" to "から", "まで" to "まで", "より" to "より", "だけ" to "だけ",
            "わ" to "は", "は" to "は", "が" to "が", "を" to "を", "に" to "に", "で" to "で",
            "と" to "と", "も" to "も", "の" to "の", "よ" to "よ", "ね" to "ね", "か" to "か",
        )

        /**
         * Applies a word's kanji to a conjugated kana form:
         *   諦める + あきらめる + あきらめない → 諦めない
         *   勉強 + べんきょう + べんきょうした → 勉強した
         * The kana tail shared by the kanji form and its reading (the okurigana) is what varies.
         */
        fun reinflect(kanjiForm: String, baseReading: String, inflected: String): String {
            var okurigana = 0
            while (okurigana < kanjiForm.length && okurigana < baseReading.length &&
                JapaneseText.isHiragana(kanjiForm[kanjiForm.length - 1 - okurigana]) &&
                kanjiForm[kanjiForm.length - 1 - okurigana] == baseReading[baseReading.length - 1 - okurigana]
            ) okurigana++
            val kanjiStem = kanjiForm.dropLast(okurigana)
            val readingStem = baseReading.dropLast(okurigana)
            return if (kanjiStem.isNotEmpty() && inflected.startsWith(readingStem)) {
                kanjiStem + inflected.drop(readingStem.length)
            } else inflected
        }
    }
}
