package io.github.otoniel2004.yomitomo.japanese

/**
 * Turns a conjugated word (in kana) back into possible dictionary forms:
 *
 *   あきらめなかった ─(かった→い)─► あきらめない ─(ない→る)─► あきらめる  (ichidan verb)
 *   かいた ─(いた→く)─► かく  (godan verb)
 *   べんきょうした ─(した→する)─► べんきょう  (suru noun)
 *
 * It only guesses; the dictionary confirms which guesses are real words of the right kind.
 * That's why each result carries the word class it must have ([WordClass]).
 */
object Deinflector {
    enum class WordClass {
        /** ichidan verb (食べる). JMdict tag v1. */
        ICHIDAN,
        /** godan verb (書く). JMdict tags v5*. */
        GODAN,
        /** する itself or a noun used with する (勉強する). JMdict tags vs, vs-i, vs-s. */
        SURU,
        /** 来る. JMdict tag vk. */
        KURU,
        /** い-adjective (高い). JMdict tag adj-i. */
        ADJECTIVE_I,
        /** Intermediate form (te-form, ない-form…) that must be deinflected further. */
        INTERMEDIATE;

        fun matches(jmdictTags: List<String>): Boolean = when (this) {
            ICHIDAN -> jmdictTags.any { it == "v1" || it == "v1-s" }
            GODAN -> jmdictTags.any { it.startsWith("v5") }
            SURU -> jmdictTags.any { it == "vs" || it.startsWith("vs-") }
            KURU -> jmdictTags.any { it == "vk" }
            ADJECTIVE_I -> jmdictTags.any { it == "adj-i" || it == "adj-ix" }
            INTERMEDIATE -> false
        }
    }

    data class Result(val base: String, val wordClass: WordClass, val steps: Int)

    private data class Rule(val from: String, val to: String, val wordClass: WordClass)

    private const val MAX_STEPS = 3

    /** Godan endings by vowel row: u (dictionary) → a, i, e, o, plus te/ta forms. */
    private val godanRows = listOf(
        // u    a    i    e    o    te      ta
        listOf("く", "か", "き", "け", "こ", "いて", "いた"),
        listOf("ぐ", "が", "ぎ", "げ", "ご", "いで", "いだ"),
        listOf("す", "さ", "し", "せ", "そ", "して", "した"),
        listOf("つ", "た", "ち", "て", "と", "って", "った"),
        listOf("ぬ", "な", "に", "ね", "の", "んで", "んだ"),
        listOf("ぶ", "ば", "び", "べ", "ぼ", "んで", "んだ"),
        listOf("む", "ま", "み", "め", "も", "んで", "んだ"),
        listOf("る", "ら", "り", "れ", "ろ", "って", "った"),
        listOf("う", "わ", "い", "え", "お", "って", "った"),
    )

    /** Endings that attach to the verb stem (the "masu stem" for godan, the bare stem for ichidan). */
    private val stemEndings = listOf(
        "ます", "ました", "ません", "ませんでした", "ましょう", "たい", "たかった", "たくない", "たくなかった",
        "ながら", "なさい", "そう",
    )

    /** Endings that attach to the negative ("a") stem for godan, the bare stem for ichidan. */
    private val negativeEndings = listOf(
        "ない", "なかった", "なくて", "なければ", "なきゃ", "ず", "ずに", "れる", "せる", "れた", "せた",
    )

    private val rules: List<Rule> = buildList {
        val (G, I) = WordClass.GODAN to WordClass.ICHIDAN

        // ── Godan ──
        for (row in godanRows) {
            val (u, a, i, e, o, te, ta) = row
            for (ending in stemEndings) add(Rule(i + ending, u, G))
            for (ending in negativeEndings) add(Rule(a + ending, u, G))
            add(Rule(e + "る", u, G)); add(Rule(e + "ば", u, G))   // potential / conditional
            add(Rule(e, u, G))                                      // imperative
            add(Rule(o + "う", u, G))                               // volitional
            add(Rule(te, u, G)); add(Rule(ta, u, G))
            add(Rule(ta + "ら", u, G)); add(Rule(ta + "り", u, G))
        }
        add(Rule("いって", "いく", G)); add(Rule("いった", "いく", G))   // 行く is irregular

        // ── Ichidan ──
        for (ending in stemEndings + negativeEndings) add(Rule(ending, "る", I))
        for (ending in listOf("た", "て", "たら", "たり", "られる", "られた", "させる", "させた", "れば", "よう", "ろ", "ちゃう", "ちゃった")) {
            add(Rule(ending, "る", I))
        }

        // ── する / 来る ──
        val suru = listOf(
            "する", "した", "して", "します", "しました", "しません", "しない", "しなかった", "しよう",
            "しろ", "される", "された", "させる", "させた", "したい", "すれば", "しちゃう", "しちゃった",
        )
        for (form in suru) add(Rule(form, "", WordClass.SURU))
        val kuru = listOf("きた", "きて", "きます", "きました", "こない", "こなかった", "こよう", "こい", "くれば", "こられる")
        for (form in kuru) add(Rule(form, "くる", WordClass.KURU))

        // ── い-adjectives ──
        for (ending in listOf("かった", "くない", "くなかった", "くて", "ければ", "く", "さ", "そう", "すぎる")) {
            add(Rule(ending, "い", WordClass.ADJECTIVE_I))
        }

        // ── Auxiliaries that reduce to the te-form (then the te-form rules continue) ──
        for (aux in listOf("いる", "いた", "います", "いました", "る", "た", "しまう", "しまった", "おく", "おいた", "ください", "あげる", "もらう", "くれる")) {
            add(Rule("て$aux", "て", WordClass.INTERMEDIATE))
            add(Rule("で$aux", "で", WordClass.INTERMEDIATE))
        }
        // Godan contraction ちゃう/じゃう (読んじゃう → 読んで)
        add(Rule("ちゃう", "て", WordClass.INTERMEDIATE)); add(Rule("ちゃった", "て", WordClass.INTERMEDIATE))
        add(Rule("じゃう", "で", WordClass.INTERMEDIATE)); add(Rule("じゃった", "で", WordClass.INTERMEDIATE))
    }

    /** Every possible dictionary form of [word] (hiragana), fewest steps first. Never includes [word] itself. */
    fun deinflect(word: String): List<Result> {
        val results = linkedMapOf<Pair<String, WordClass>, Result>()
        var frontier = listOf(word)
        for (step in 1..MAX_STEPS) {
            val next = mutableListOf<String>()
            for (form in frontier) {
                for (rule in rules) {
                    if (!form.endsWith(rule.from)) continue
                    val base = form.dropLast(rule.from.length) + rule.to
                    if (base.isEmpty() || base == word) continue
                    // Every form is also tried as an input to further rules (ない-forms, te-forms…).
                    next += base
                    if (rule.wordClass != WordClass.INTERMEDIATE) {
                        results.putIfAbsent(base to rule.wordClass, Result(base, rule.wordClass, step))
                    }
                }
            }
            frontier = next.distinct()
        }
        return results.values.sortedBy { it.steps }
    }

    private operator fun <T> List<T>.component6() = this[5]
    private operator fun <T> List<T>.component7() = this[6]
}
