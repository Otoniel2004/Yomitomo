package io.github.otoniel2004.yomitomo.data

import kotlinx.serialization.Serializable

/** Kind of item saved on the home screen. */
enum class ItemType { WORD, EXPRESSION, SENTENCE }

/** Simplified part of speech — used to highlight particles in sentences. */
enum class PartOfSpeech {
    NOUN, PRONOUN, VERB, ADJECTIVE, ADVERB, PARTICLE, AUXILIARY,
    CONJUNCTION, INTERJECTION, PREFIX, SUFFIX, PUNCTUATION, OTHER,
}

/**
 * A piece of a sentence — the "token" the tokenizer returns.
 * reading: hiragana reading (null when there is nothing to read, e.g. punctuation).
 */
@Serializable
data class Segment(
    val surface: String,
    val reading: String? = null,
    val pos: PartOfSpeech = PartOfSpeech.OTHER,
)
