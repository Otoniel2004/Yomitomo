package io.github.otoniel2004.mangareaderjp.translation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLClientTest {
    private val client = DeepLClient { "not-used-in-these-tests" }

    @Test
    fun `request always names both languages`() {
        // Regression: default values were left out of the JSON and DeepL answered HTTP 400.
        val body = client.requestBody(listOf("猫"), context = null)
        assertTrue(body, body.contains("\"source_lang\":\"JA\""))
        assertTrue(body, body.contains("\"target_lang\":\"EN-US\""))
        assertTrue(body, body.contains("\"text\":[\"猫\"]"))
    }

    @Test
    fun `context is sent only when present`() {
        assertFalse(client.requestBody(listOf("猫"), context = null).contains("context"))
        assertFalse(client.requestBody(listOf("猫"), context = "  ").contains("context"))
        assertTrue(client.requestBody(listOf("猫"), context = "前のページ").contains("\"context\":\"前のページ\""))
    }
}
