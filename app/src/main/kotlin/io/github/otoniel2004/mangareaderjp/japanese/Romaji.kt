package io.github.otoniel2004.mangareaderjp.japanese

import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.Segment

/**
 * Converts kana to romaji (Hepburn, "typing" style: long vowels doubled, e.g. koohii).
 *
 * fromKana, step by step:
 *   1. katakana → hiragana (both scripts are pronounced the same)
 *   2. try 2 characters together first (きゃ → kya, ふぁ → fa)
 *   3. otherwise 1 character (か → ka)
 *   4. special cases:
 *        っ  → doubles the next consonant (まって → matte, まっち → matchi)
 *        ん  → n, or n' before a vowel/y (きんえん → kin'en)
 *        ー  → repeats the previous vowel (こーひー → koohii)
 *   5. kanji without a reading is kept as is (better than guessing)
 */
object Romaji {
    private val digraphs = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho", "しぇ" to "she",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo", "じぇ" to "je",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho", "ちぇ" to "che",
        "ぢゃ" to "ja", "ぢゅ" to "ju", "ぢょ" to "jo",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        // combinations used in foreign words (katakana)
        "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
        "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du",
        "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo",
        "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
        "つぁ" to "tsa", "つぇ" to "tse", "つぉ" to "tso",
    )

    private val mono = mapOf(
        'あ' to "a", 'い' to "i", 'う' to "u", 'え' to "e", 'お' to "o",
        'か' to "ka", 'き' to "ki", 'く' to "ku", 'け' to "ke", 'こ' to "ko",
        'が' to "ga", 'ぎ' to "gi", 'ぐ' to "gu", 'げ' to "ge", 'ご' to "go",
        'さ' to "sa", 'し' to "shi", 'す' to "su", 'せ' to "se", 'そ' to "so",
        'ざ' to "za", 'じ' to "ji", 'ず' to "zu", 'ぜ' to "ze", 'ぞ' to "zo",
        'た' to "ta", 'ち' to "chi", 'つ' to "tsu", 'て' to "te", 'と' to "to",
        'だ' to "da", 'ぢ' to "ji", 'づ' to "zu", 'で' to "de", 'ど' to "do",
        'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
        'は' to "ha", 'ひ' to "hi", 'ふ' to "fu", 'へ' to "he", 'ほ' to "ho",
        'ば' to "ba", 'び' to "bi", 'ぶ' to "bu", 'べ' to "be", 'ぼ' to "bo",
        'ぱ' to "pa", 'ぴ' to "pi", 'ぷ' to "pu", 'ぺ' to "pe", 'ぽ' to "po",
        'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
        'や' to "ya", 'ゆ' to "yu", 'よ' to "yo",
        'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
        'わ' to "wa", 'ゐ' to "wi", 'ゑ' to "we", 'を' to "o", 'ゔ' to "vu",
        'ぁ' to "a", 'ぃ' to "i", 'ぅ' to "u", 'ぇ' to "e", 'ぉ' to "o",
        'ゃ' to "ya", 'ゅ' to "yu", 'ょ' to "yo", 'ゎ' to "wa",
        'ゕ' to "ka", 'ゖ' to "ke",
    )

    private val punctuation = mapOf(
        '。' to ".", '、' to ",", '！' to "!", '？' to "?", '　' to " ",
        '「' to "\"", '」' to "\"", '『' to "\"", '』' to "\"",
        '（' to "(", '）' to ")", '…' to "...", '～' to "~", '〜' to "~", '・' to " ",
    )

    private const val VOWELS = "aeiou"

    fun fromKana(text: String): String {
        val s = JapaneseText.toHiragana(text)
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val pair = if (i + 1 < s.length) digraphs[s.substring(i, i + 2)] else null
            if (pair != null) {
                out.append(pair)
                i += 2
                continue
            }
            when (val c = s[i]) {
                'っ' -> {
                    val next = syllableAt(s, i + 1)
                    if (next != null && next[0] !in VOWELS) {
                        out.append(if (next.startsWith("ch")) 't' else next[0])
                    }
                }
                'ん' -> {
                    val next = syllableAt(s, i + 1)
                    out.append(if (next != null && (next[0] in VOWELS || next[0] == 'y')) "n'" else "n")
                }
                'ー' -> out.lastOrNull { it in VOWELS }?.let { out.append(it) }
                else -> out.append(mono[c] ?: punctuation[c] ?: c.toString())
            }
            i++
        }
        return out.toString()
    }

    /**
     * Romaji of an already tokenized sentence, with spaces between words.
     * Glued to the previous word: auxiliaries (ない, る), suffixes, punctuation and て/で after a verb.
     * Particles with special pronunciation: は → wa, へ → e, を → o.
     *
     *   明日、駅の前で待ってるね。 → "ashita, eki no mae de matteru ne."
     */
    fun fromSegments(segments: List<Segment>): String {
        val kana = StringBuilder()
        segments.forEachIndexed { index, segment ->
            val previous = segments.getOrNull(index - 1)
            val glue = index == 0 ||
                (segment.pos in GLUED && !isStandaloneCopula(segment, previous)) ||
                previous?.pos == PartOfSpeech.PREFIX ||
                (segment.pos == PartOfSpeech.PARTICLE && segment.surface in TE_FORM &&
                    previous?.pos in VERBAL)
            if (!glue) kana.append(' ')
            kana.append(spoken(segment))
        }
        return fromKana(kana.toString())
    }

    /**
     * The copula (です, だ…) is written as its own word after nouns and adjectives
     * (天気です → tenki desu), but past-tense だ after a verb stays glued (読んだ → yonda).
     */
    private fun isStandaloneCopula(segment: Segment, previous: Segment?) =
        segment.surface in COPULA && previous?.pos != PartOfSpeech.VERB && previous?.pos != PartOfSpeech.AUXILIARY

    private fun spoken(segment: Segment): String {
        if (segment.pos == PartOfSpeech.PARTICLE) {
            when (segment.surface) {
                "は" -> return "わ"
                "へ" -> return "え"
                "を" -> return "お"
            }
        }
        return segment.reading ?: segment.surface
    }

    private fun syllableAt(s: String, index: Int): String? {
        if (index >= s.length) return null
        if (index + 1 < s.length) digraphs[s.substring(index, index + 2)]?.let { return it }
        return mono[s[index]]
    }

    private val COPULA = setOf("です", "でした", "でしょう", "だ", "だった", "だろう", "じゃ", "じゃない")
    private val GLUED = setOf(PartOfSpeech.AUXILIARY, PartOfSpeech.SUFFIX, PartOfSpeech.PUNCTUATION)
    private val VERBAL = setOf(PartOfSpeech.VERB, PartOfSpeech.AUXILIARY, PartOfSpeech.ADJECTIVE)
    private val TE_FORM = setOf("て", "で")
}
