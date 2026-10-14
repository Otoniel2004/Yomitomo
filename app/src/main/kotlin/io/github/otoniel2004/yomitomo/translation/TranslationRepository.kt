package io.github.otoniel2004.yomitomo.translation

/** Translations already paid for, kept on the phone (source text → English). */
interface TranslationCache {
    suspend fun get(sources: List<String>): Map<String, String>
    suspend fun put(translations: Map<String, String>)
}

/**
 * Translates with a cache in front of DeepL, so the same text is never paid for twice:
 *
 *   texts ─► distinct ─► cached? ─yes─► reuse
 *                           └─no──► DeepL in batches of 50 (+ context) ─► save to cache
 *
 * [translateRemote] is the DeepL call, injected so this logic can be tested without the network.
 */
class TranslationRepository(
    private val cache: TranslationCache,
    private val translateRemote: (texts: List<String>, context: String?) -> List<String>,
    private val hasKey: () -> Boolean,
) {
    val isConfigured: Boolean get() = hasKey()

    /** Japanese → English for every text; throws [TranslationException] on failure. */
    suspend fun translate(texts: List<String>, context: String? = null): Map<String, String> {
        val distinct = texts.map(String::trim).filter(String::isNotEmpty).distinct()
        if (distinct.isEmpty()) return emptyMap()
        val cached = cache.get(distinct)
        val missing = distinct.filterNot(cached::containsKey)
        if (missing.isEmpty()) return cached

        if (!hasKey()) throw TranslationException.NoKey()
        val fresh = mutableMapOf<String, String>()
        for (batch in missing.chunked(DeepLClient.MAX_TEXTS)) {
            val results = translateRemote(batch, context?.takeLast(MAX_CONTEXT))
            batch.zip(results).forEach { (source, english) -> fresh[source] = english }
        }
        cache.put(fresh)
        return cached + fresh
    }

    private companion object {
        /** Enough context to help the translation without sending a whole chapter. */
        const val MAX_CONTEXT = 1_500
    }
}
