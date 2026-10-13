package io.github.otoniel2004.mangareaderjp

import android.app.Application
import android.content.Context
import io.github.otoniel2004.mangareaderjp.cards.CardDraftBuilder
import io.github.otoniel2004.mangareaderjp.data.AppDatabase
import io.github.otoniel2004.mangareaderjp.data.SampleData
import io.github.otoniel2004.mangareaderjp.data.SavedItemRepository
import io.github.otoniel2004.mangareaderjp.data.SettingsRepository
import io.github.otoniel2004.mangareaderjp.dictionary.JmDictionary
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseAnalyzer
import io.github.otoniel2004.mangareaderjp.japanese.KanaKanjiConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MangaReaderApp : Application() {
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
    val items = SavedItemRepository(database.savedItemDao(), context)
    val settings = SettingsRepository(context)

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
