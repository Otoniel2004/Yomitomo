package io.github.otoniel2004.mangareaderjp.dictionary

import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech

/** One meaning of a dictionary entry. pos/misc are JMdict tag names ("v1", "n", "uk"...). */
data class DictSense(val pos: List<String>, val misc: List<String>, val glosses: List<String>)

/**
 * A JMdict entry. kanji/readings are sorted most common first.
 * priority: lower = more common (see the dictionary builder).
 */
data class DictEntry(
    val id: Long,
    val kanji: List<String>,
    val readings: List<String>,
    val senses: List<DictSense>,
    val priority: Int,
    val usuallyKana: Boolean,
    /** JLPT level 5..1 (N5 = beginner … N1 = advanced), or 0 when the word is in no JLPT list. */
    val jlpt: Int = 0,
) {
    val jlptLabel: String? get() = if (jlpt in 1..5) "N$jlpt" else null

    val posTags: List<String> get() = senses.firstOrNull()?.pos.orEmpty()

    val isExpression: Boolean get() = "exp" in posTags

    /** Up to [maxSenses] meanings, each sense's glosses joined: "to give up, to abandon". */
    fun meanings(maxSenses: Int = 3): List<String> =
        senses.take(maxSenses).map { it.glosses.joinToString(", ") }

    /** How the word is normally written: kana for "usually kana" words, otherwise its first kanji form. */
    val preferredForm: String get() = if (usuallyKana || kanji.isEmpty()) readings.first() else kanji.first()

    val partOfSpeech: PartOfSpeech get() = partOfSpeechOf(posTags)
}

/** Maps JMdict part-of-speech tags to our simplified classes. */
fun partOfSpeechOf(tags: List<String>): PartOfSpeech {
    for (tag in tags) {
        when {
            tag == "pn" -> return PartOfSpeech.PRONOUN
            tag.startsWith("v") && tag != "vs" -> return PartOfSpeech.VERB
            tag.startsWith("adj") -> return PartOfSpeech.ADJECTIVE
            tag.startsWith("adv") -> return PartOfSpeech.ADVERB
            tag == "prt" -> return PartOfSpeech.PARTICLE
            tag.startsWith("aux") -> return PartOfSpeech.AUXILIARY
            tag == "conj" -> return PartOfSpeech.CONJUNCTION
            tag == "int" -> return PartOfSpeech.INTERJECTION
            tag == "pref" -> return PartOfSpeech.PREFIX
            tag == "suf" -> return PartOfSpeech.SUFFIX
            tag.startsWith("n") -> return PartOfSpeech.NOUN
        }
    }
    return PartOfSpeech.OTHER
}

/** Read-only dictionary lookups. Calls block, so use them off the main thread. */
interface Dictionary {
    /** Entries whose reading (in hiragana) is exactly [hiragana], most common first. */
    fun byReading(hiragana: String, limit: Int = 20): List<DictEntry>

    /** Entries written exactly as [text] (kanji form or kana reading), most common first. */
    fun byText(text: String, limit: Int = 20): List<DictEntry>
}
