package io.github.otoniel2004.mangareaderjp.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import io.github.otoniel2004.mangareaderjp.srs.Rating
import io.github.otoniel2004.mangareaderjp.srs.Srs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SavedItemRepository(private val dao: SavedItemDao) {
    fun observeAll(): Flow<List<SavedItem>> = dao.observeAll()

    fun observe(id: Long): Flow<SavedItem?> = dao.observe(id)

    suspend fun dueItems(now: Long): List<SavedItem> = dao.due(now)

    suspend fun insertAll(items: List<SavedItem>) = dao.insertAll(items)

    suspend fun setShowFurigana(id: Long, show: Boolean) = dao.setShowFurigana(id, show)

    suspend fun review(item: SavedItem, rating: Rating, now: Long): SavedItem {
        val updated = item.copy(srs = Srs.schedule(item.srs, rating, now))
        dao.update(updated)
        return updated
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Reader preferences (what is drawn on top of the manga). */
data class ReaderSettings(
    val showFuriganaOnPage: Boolean = true,
    val showStatusDot: Boolean = true,
    val keepScreenOn: Boolean = true,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Simple settings stored with DataStore (the app preferences file).
 * Secrets (like the DeepL key) do NOT live here: they get their own encrypted storage.
 */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val FURIGANA_ON_PAGE = booleanPreferencesKey("furigana_on_page")
        val STATUS_DOT = booleanPreferencesKey("status_dot")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SEEDED = booleanPreferencesKey("seeded_samples")
    }

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            showFuriganaOnPage = prefs[Keys.FURIGANA_ON_PAGE] ?: true,
            showStatusDot = prefs[Keys.STATUS_DOT] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
        )
    }

    suspend fun setShowFuriganaOnPage(value: Boolean) = set(Keys.FURIGANA_ON_PAGE, value)
    suspend fun setShowStatusDot(value: Boolean) = set(Keys.STATUS_DOT, value)
    suspend fun setKeepScreenOn(value: Boolean) = set(Keys.KEEP_SCREEN_ON, value)

    suspend fun isSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false
    suspend fun markSeeded() = set(Keys.SEEDED, true)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
