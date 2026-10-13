package io.github.otoniel2004.mangareaderjp.translation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Translation failures, with a message that can be shown to the user as is. */
sealed class TranslationException(message: String) : Exception(message) {
    class NoKey : TranslationException("Add your DeepL API key in Settings to translate.")
    class InvalidKey : TranslationException("DeepL rejected the API key. Check it in Settings.")
    class QuotaExceeded : TranslationException("Your DeepL monthly character quota is used up.")
    class RateLimited : TranslationException("Too many requests to DeepL. Try again in a moment.")
    class Network(cause: Throwable) : TranslationException("No connection to DeepL (${cause.javaClass.simpleName}).")
    class Http(code: Int) : TranslationException("DeepL error (HTTP $code).")
}

data class DeepLUsage(val used: Long, val limit: Long)

/**
 * Minimal DeepL REST client (https://developers.deepl.com/docs/api-reference).
 *
 *   Free keys end in ":fx" and use api-free.deepl.com; Pro keys use api.deepl.com.
 *   The key goes only in the Authorization header and is never logged.
 *
 * Calls block: use them off the main thread.
 */
class DeepLClient(private val apiKey: () -> String?) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TranslateRequest(
        val text: List<String>,
        @SerialName("source_lang") val sourceLang: String = "JA",
        @SerialName("target_lang") val targetLang: String = "EN-US",
        /** Extra text that helps the translation (e.g. the previous page) and is not billed. */
        val context: String? = null,
    )

    @Serializable
    private data class TranslateResponse(val translations: List<Translated>)

    @Serializable
    private data class Translated(val text: String)

    @Serializable
    private data class UsageResponse(
        @SerialName("character_count") val characterCount: Long,
        @SerialName("character_limit") val characterLimit: Long,
    )

    /** Translates [texts] (Japanese → English), in order. At most 50 texts per call (DeepL limit). */
    fun translate(texts: List<String>, context: String? = null): List<String> {
        require(texts.size <= MAX_TEXTS) { "DeepL accepts at most $MAX_TEXTS texts per request" }
        val body = json.encodeToString(TranslateRequest.serializer(), TranslateRequest(texts, context = context?.takeIf { it.isNotBlank() }))
        val response = json.decodeFromString(TranslateResponse.serializer(), request("/v2/translate", body))
        return response.translations.map { it.text }
    }

    fun usage(): DeepLUsage {
        val usage = json.decodeFromString(UsageResponse.serializer(), request("/v2/usage", null))
        return DeepLUsage(usage.characterCount, usage.characterLimit)
    }

    private fun request(path: String, body: String?): String {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: throw TranslationException.NoKey()
        val host = if (key.endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com"
        try {
            val connection = URI(host + path).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("Authorization", "DeepL-Auth-Key $key")
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                when (val code = connection.responseCode) {
                    in 200..299 -> return connection.inputStream.bufferedReader().use { it.readText() }
                    401, 403 -> throw TranslationException.InvalidKey()
                    456 -> throw TranslationException.QuotaExceeded()
                    429 -> throw TranslationException.RateLimited()
                    else -> throw TranslationException.Http(code)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: IOException) {
            throw TranslationException.Network(e)
        }
    }

    companion object {
        const val MAX_TEXTS = 50
    }
}
