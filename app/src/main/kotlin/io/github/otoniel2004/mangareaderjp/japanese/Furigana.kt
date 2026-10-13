package io.github.otoniel2004.mangareaderjp.japanese

/** Japanese character classification by Unicode block. */
object JapaneseText {
    fun isKanji(c: Char) =
        c in '一'..'鿿' ||   // common kanji
            c in '㐀'..'䶿' ||   // extension A (rare)
            c in '豈'..'﫿' ||   // compatibility kanji
            c == '々' || c == '〆'

    fun isHiragana(c: Char) = c in 'ぁ'..'ゖ' || c == 'ゝ' || c == 'ゞ'

    fun isKatakana(c: Char) =
        c in 'ァ'..'ヺ' ||   // ァ..ヺ
            c in 'ー'..'ヾ' ||   // ー ヽ ヾ
            c in 'ㇰ'..'ㇿ'      // small katakana (Ainu)

    /** Katakana → hiragana: the two Unicode blocks are 0x60 apart (ア=30A2, あ=3042). */
    fun toHiragana(text: String) = buildString(text.length) {
        for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
    }

    /** Kanji and katakana get furigana; plain hiragana and punctuation don't. */
    fun needsFurigana(surface: String) = surface.any { isKanji(it) || isKatakana(it) }
}

/** A piece of text with the reading (ruby) shown above it, if any. */
data class RubyPart(val text: String, val ruby: String?)

/**
 * Splits a word so that furigana only sits over the part that needs it.
 *
 *   食べる   + たべる   → [食|た] [べる]
 *   お茶     + おちゃ   → [お] [茶|ちゃ]
 *   ハマった + はまった → [ハマ|はま] [った]
 *   コーヒー + こーひー → [コーヒー|こーひー]
 *
 * Works by trimming kana that is identical in the word and in the reading, from both ends.
 */
fun rubyParts(surface: String, reading: String?): List<RubyPart> {
    if (reading.isNullOrEmpty() || !JapaneseText.needsFurigana(surface)) {
        return listOf(RubyPart(surface, null))
    }

    var end = surface.length
    var readingEnd = reading.length
    while (end > 0 && readingEnd > 0 &&
        JapaneseText.isHiragana(surface[end - 1]) && surface[end - 1] == reading[readingEnd - 1]
    ) {
        end--
        readingEnd--
    }

    var start = 0
    var readingStart = 0
    while (start < end && readingStart < readingEnd &&
        JapaneseText.isHiragana(surface[start]) && surface[start] == reading[readingStart]
    ) {
        start++
        readingStart++
    }

    val core = surface.substring(start, end)
    val coreReading = reading.substring(readingStart, readingEnd)
    // A reading that doesn't fit the word: better no ruby than a wrong one.
    if (core.isEmpty() || coreReading.isEmpty()) return listOf(RubyPart(surface, null))

    return buildList {
        if (start > 0) add(RubyPart(surface.substring(0, start), null))
        add(RubyPart(core, coreReading))
        if (end < surface.length) add(RubyPart(surface.substring(end), null))
    }
}
