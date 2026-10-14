package io.github.otoniel2004.yomitomo.translation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRepositoryTest {
    private class MemoryCache : TranslationCache {
        val stored = mutableMapOf<String, String>()
        override suspend fun get(sources: List<String>) = sources.filter(stored::containsKey).associateWith { stored.getValue(it) }
        override suspend fun put(translations: Map<String, String>) { stored += translations }
    }

    /** Fake DeepL: "EN(text)", remembering every call. */
    private class FakeDeepL {
        val calls = mutableListOf<Pair<List<String>, String?>>()
        fun translate(texts: List<String>, context: String?): List<String> {
            calls += texts to context
            return texts.map { "EN($it)" }
        }
    }

    private val cache = MemoryCache()
    private val deepL = FakeDeepL()
    private fun repository(hasKey: Boolean = true) = TranslationRepository(cache, deepL::translate) { hasKey }

    @Test
    fun `translates and caches, so the same text is never paid for twice`() = runBlocking {
        val repo = repository()
        assertEquals(mapOf("猫" to "EN(猫)"), repo.translate(listOf("猫")))
        repo.translate(listOf("猫"))
        assertEquals(1, deepL.calls.size)
    }

    @Test
    fun `only missing texts are sent, duplicates and blanks once`() = runBlocking {
        cache.stored["犬"] = "dog"
        val result = repository().translate(listOf("犬", "猫", "猫", "  ", "鳥"), context = "前のページ")
        assertEquals(mapOf("犬" to "dog", "猫" to "EN(猫)", "鳥" to "EN(鳥)"), result)
        assertEquals(listOf(listOf("猫", "鳥") to "前のページ"), deepL.calls)
    }

    @Test
    fun `large pages are split into batches of 50`() = runBlocking {
        val texts = (1..120).map { "文$it" }
        assertEquals(120, repository().translate(texts).size)
        assertEquals(listOf(50, 50, 20), deepL.calls.map { it.first.size })
    }

    @Test
    fun `no key means no call, but cached texts still work`() = runBlocking {
        cache.stored["犬"] = "dog"
        assertEquals(mapOf("犬" to "dog"), repository(hasKey = false).translate(listOf("犬")))
        val failure = runCatching { repository(hasKey = false).translate(listOf("猫")) }.exceptionOrNull()
        assertTrue(failure is TranslationException.NoKey)
        assertTrue(deepL.calls.isEmpty())
    }
}
