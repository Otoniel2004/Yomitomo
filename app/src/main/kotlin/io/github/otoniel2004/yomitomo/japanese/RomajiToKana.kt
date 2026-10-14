package io.github.otoniel2004.yomitomo.japanese

/**
 * Romaji → kana, following the same rules as a Japanese phone keyboard ("wapuro" romaji):
 *
 *   shi / si → し     chi / ti → ち     tsu / tu → つ     fu / hu → ふ
 *   kk, tt, ss, pp…  → っ + consonant   (matte → まって, kitto → きっと)
 *   tch              → っち             (matchi / match → まっち)
 *   nn, n', or n before a consonant → ん  (konnichiwa → こんにちわ, san'po → さんぽ)
 *   -                → ー                (ko-hi- → こーひー)
 *   a word in CAPITALS → katakana         (KOOHII → コーヒー)
 *
 * Characters that are already Japanese pass through unchanged.
 */
object RomajiToKana {
    private val table: Map<String, String> = buildMap {
        // Basic syllables
        val rows = mapOf(
            "" to "あいうえお", "k" to "かきくけこ", "g" to "がぎぐげご", "s" to "さしすせそ",
            "z" to "ざじずぜぞ", "t" to "たちつてと", "d" to "だぢづでど", "n" to "なにぬねの",
            "h" to "はひふへほ", "b" to "ばびぶべぼ", "p" to "ぱぴぷぺぽ", "m" to "まみむめも",
            "r" to "らりるれろ",
        )
        val vowels = "aiueo"
        for ((consonant, kana) in rows) {
            vowels.forEachIndexed { i, v -> put(consonant + v, kana[i].toString()) }
        }
        putAll(
            mapOf(
                "ya" to "や", "yu" to "ゆ", "yo" to "よ", "wa" to "わ", "wo" to "を", "wi" to "うぃ", "we" to "うぇ",
                "shi" to "し", "chi" to "ち", "tsu" to "つ", "fu" to "ふ", "ji" to "じ",
                "l" to "", // handled with small kana below
                "la" to "ぁ", "li" to "ぃ", "lu" to "ぅ", "le" to "ぇ", "lo" to "ぉ",
                "xa" to "ぁ", "xi" to "ぃ", "xu" to "ぅ", "xe" to "ぇ", "xo" to "ぉ",
                "lya" to "ゃ", "lyu" to "ゅ", "lyo" to "ょ", "xya" to "ゃ", "xyu" to "ゅ", "xyo" to "ょ",
                "ltu" to "っ", "xtu" to "っ", "ltsu" to "っ", "xtsu" to "っ",
                "va" to "ゔぁ", "vi" to "ゔぃ", "vu" to "ゔ", "ve" to "ゔぇ", "vo" to "ゔぉ",
                "fa" to "ふぁ", "fi" to "ふぃ", "fe" to "ふぇ", "fo" to "ふぉ",
                "thi" to "てぃ", "dhi" to "でぃ", "twu" to "とぅ", "dwu" to "どぅ",
                "tsa" to "つぁ", "tse" to "つぇ", "tso" to "つぉ",
                "she" to "しぇ", "che" to "ちぇ", "je" to "じぇ",
            ),
        )
        remove("l")
        // Combined syllables: ki + ya → きゃ etc.
        val yRows = mapOf(
            "ky" to "き", "gy" to "ぎ", "sy" to "し", "zy" to "じ", "ty" to "ち", "dy" to "ぢ", "ny" to "に",
            "hy" to "ひ", "by" to "び", "py" to "ぴ", "my" to "み", "ry" to "り", "cy" to "ち",
        )
        for ((prefix, base) in yRows) {
            put(prefix + "a", base + "ゃ"); put(prefix + "u", base + "ゅ"); put(prefix + "o", base + "ょ")
        }
        for ((prefix, base) in mapOf("sh" to "し", "ch" to "ち", "j" to "じ")) {
            put(prefix + "a", base + "ゃ"); put(prefix + "u", base + "ゅ"); put(prefix + "o", base + "ょ")
        }
        put("-", "ー")
        put(".", "。"); put(",", "、"); put("?", "？"); put("!", "！")
    }

    private const val MAX_KEY = 4
    private const val VOWELS = "aiueo"

    /** Converts text word by word; CAPITALIZED words become katakana, everything else hiragana. */
    fun convert(text: String): String = text.split(" ").joinToString(" ") { word ->
        val letters = word.filter(Char::isLetter)
        val katakana = letters.length > 1 && letters.all { it.isUpperCase() }
        val hiragana = toHiragana(word.lowercase())
        if (katakana) toKatakana(hiragana) else hiragana
    }

    fun toHiragana(input: String): String {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val next = s.getOrNull(i + 1)

            // n: ん when doubled, before an apostrophe, before a consonant (not y), or at the end.
            // "nn" + vowel is ん + na/ni/…: konnichiwa → こんにちわ, onna → おんな.
            if (c == 'n') {
                val afterNext = s.getOrNull(i + 2)
                when {
                    next == 'n' && afterNext != null && (afterNext in VOWELS || afterNext == 'y') -> {
                        out.append('ん'); i++; continue
                    }
                    next == 'n' -> { out.append('ん'); i += 2; continue }
                    next == '\'' -> { out.append('ん'); i += 2; continue }
                    next == null || (next !in VOWELS && next != 'y') -> { out.append('ん'); i++; continue }
                }
            }
            // Double consonant → small tsu (tch → っち as well).
            if (next != null && c == next && c.isLetter() && c !in VOWELS && c != 'n') {
                out.append('っ'); i++; continue
            }
            if (c == 't' && next == 'c' && s.getOrNull(i + 2) == 'h') {
                out.append('っ'); i++; continue
            }

            var matched = false
            for (length in MAX_KEY downTo 1) {
                if (i + length > s.length) continue
                val kana = table[s.substring(i, i + length)] ?: continue
                out.append(kana)
                i += length
                matched = true
                break
            }
            if (!matched) {
                out.append(c) // unknown letter or a character that is already Japanese
                i++
            }
        }
        return out.toString()
    }

    fun toKatakana(hiragana: String) = buildString(hiragana.length) {
        for (c in hiragana) append(if (c in 'ぁ'..'ゖ') c + 0x60 else c)
    }

    /** True if the text has Latin letters to convert (as opposed to being typed in Japanese already). */
    fun looksLikeRomaji(text: String) = text.any { it in 'a'..'z' || it in 'A'..'Z' }
}
