package io.github.otoniel2004.yomitomo.translation

import io.github.otoniel2004.yomitomo.data.TranslationCacheDao
import io.github.otoniel2004.yomitomo.data.TranslationCacheEntry

/** [TranslationCache] stored in the app database, so translations survive restarts. */
class RoomTranslationCache(private val dao: TranslationCacheDao) : TranslationCache {
    override suspend fun get(sources: List<String>): Map<String, String> =
        // SQLite limits how many values an IN (...) query may take.
        sources.chunked(500).flatMap { dao.get(it) }.associate { it.source to it.translation }

    override suspend fun put(translations: Map<String, String>) {
        val now = System.currentTimeMillis()
        dao.put(translations.map { (source, english) -> TranslationCacheEntry(source, english, now) })
    }
}
