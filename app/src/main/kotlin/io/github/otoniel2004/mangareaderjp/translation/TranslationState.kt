package io.github.otoniel2004.mangareaderjp.translation

/** Where a bubble's English translation is, as shown in the reader popup. */
sealed interface TranslationState {
    data object Loading : TranslationState
    data class Done(val english: String) : TranslationState
    data class Failed(val message: String) : TranslationState
}
