package io.github.otoniel2004.mangareaderjp

import android.app.Application
import android.content.Context
import io.github.otoniel2004.mangareaderjp.data.AppDatabase
import io.github.otoniel2004.mangareaderjp.data.SampleData
import io.github.otoniel2004.mangareaderjp.data.SavedItemRepository
import io.github.otoniel2004.mangareaderjp.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database = AppDatabase.build(context)
    val items = SavedItemRepository(database.savedItemDao())
    val settings = SettingsRepository(context)

    init {
        scope.launch {
            if (!settings.isSeeded()) {
                items.insertAll(SampleData.items(System.currentTimeMillis()))
                settings.markSeeded()
            }
        }
    }
}
