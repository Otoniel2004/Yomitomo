package io.github.otoniel2004.yomitomo

import android.app.Application
import android.content.Context
import io.github.otoniel2004.yomitomo.cards.CardDraftBuilder
import io.github.otoniel2004.yomitomo.data.AppDatabase
import io.github.otoniel2004.yomitomo.data.BackupManager
import io.github.otoniel2004.yomitomo.data.SampleData
import io.github.otoniel2004.yomitomo.data.SavedItemRepository
import io.github.otoniel2004.yomitomo.data.SecretStore
import io.github.otoniel2004.yomitomo.data.SettingsRepository
import io.github.otoniel2004.yomitomo.translation.DeepLClient
import io.github.otoniel2004.yomitomo.translation.OfflineTranslator
import io.github.otoniel2004.yomitomo.translation.RoomTranslationCache
import io.github.otoniel2004.yomitomo.translation.TranslationRepository
import io.github.otoniel2004.yomitomo.dictionary.JmDictionary
import io.github.otoniel2004.yomitomo.japanese.JapaneseAnalyzer
import io.github.otoniel2004.yomitomo.japanese.KanaKanjiConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YomitomoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * The app's dependency box: creates the database and repositories once
 * and hands them to the screens. (Manual dependency injection, no library.)
 */
class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = AppDatabase.build(context)
    val items = SavedItemRepository(database.savedItemDao(), context, database.reviewLogDao())
    val settings = SettingsRepository(context)
    val backup = BackupManager(database.savedItemDao()) { items.imagesDir() }

    val secrets = SecretStore(context)
    val deepL = DeepLClient { secrets.get(SecretStore.DEEPL_API_KEY) }
    val translations = TranslationRepository(
        cache = RoomTranslationCache(database.translationCacheDao()),
        translateRemote = deepL::translate,
        hasKey = { secrets.get(SecretStore.DEEPL_API_KEY) != null },
    )

    /** On-device Japanese → English (downloads its model on first use). */
    val offlineTranslator by lazy { OfflineTranslator() }

    val dictionary = JmDictionary(context)
    val analyzer = JapaneseAnalyzer()
    val converter = KanaKanjiConverter(dictionary)
    val drafts = CardDraftBuilder(dictionary, analyzer)

    private val languageToolsReady = MutableStateFlow(false)

    /** True once the dictionary is installed and the tokenizer is loaded (a few seconds on first launch). */
    val languageReady: StateFlow<Boolean> = languageToolsReady.asStateFlow()

    init {
        scope.launch {
            if (!settings.isSeeded()) {
                items.insertAll(SampleData.items(System.currentTimeMillis()))
                settings.markSeeded()
            }
        }
        scope.launch {
            dictionary.open()
            analyzer.warmUp()
            languageToolsReady.value = true
        }
    }
}
