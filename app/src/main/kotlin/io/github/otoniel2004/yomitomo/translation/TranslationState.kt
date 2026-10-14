package io.github.otoniel2004.yomitomo.translation

/** Where one translation is. */
sealed interface TranslationState {
    data object Loading : TranslationState
    data class Done(val english: String) : TranslationState
    data class Failed(val message: String) : TranslationState
}

/**
 * Both translations of a bubble, shown side by side in the reader popup:
 *   deepL   – best quality; null when there's no key
 *   offline – on-device, always attempted; the fallback when DeepL fails
 */
data class BubbleTranslations(
    val deepL: TranslationState? = null,
    val offline: TranslationState? = null,
) {
    /** The translation to save with a card: DeepL when it worked, otherwise the on-device one. */
    val best: String? get() = (deepL as? TranslationState.Done)?.english ?: (offline as? TranslationState.Done)?.english
}
