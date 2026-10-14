package io.github.otoniel2004.yomitomo.translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Japanese → English on the phone itself (Google ML Kit Translate): free, no quota, works
 * without internet. The language model (~30 MB) is downloaded once, the first time it's used.
 * Quality is below DeepL, so it's shown as a second opinion and as the fallback.
 *
 * Calls block: use them off the main thread.
 */
class OfflineTranslator {
    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build(),
    )

    @Volatile private var modelReady = false

    fun translate(text: String): String {
        if (!modelReady) {
            Tasks.await(translator.downloadModelIfNeeded())
            modelReady = true
        }
        return Tasks.await(translator.translate(text))
    }
}
