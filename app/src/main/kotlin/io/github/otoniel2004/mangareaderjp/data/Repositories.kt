package io.github.otoniel2004.mangareaderjp.data

import android.content.Context
import java.io.File
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import io.github.otoniel2004.mangareaderjp.srs.Rating
import io.github.otoniel2004.mangareaderjp.srs.Srs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SavedItemRepository(private val dao: SavedItemDao, private val context: Context) {
    fun observeAll(): Flow<List<SavedItem>> = dao.observeAll()

    fun observe(id: Long): Flow<SavedItem?> = dao.observe(id)

    suspend fun dueItems(now: Long): List<SavedItem> = dao.due(now)

    suspend fun insertAll(items: List<SavedItem>) = dao.insertAll(items)

    suspend fun insert(item: SavedItem): Long = dao.insert(item)

    suspend fun setShowFurigana(id: Long, show: Boolean) = dao.setShowFurigana(id, show)

    suspend fun review(item: SavedItem, rating: Rating, now: Long): SavedItem {
        val updated = item.copy(srs = Srs.schedule(item.srs, rating, now))
        dao.update(updated)
        return updated
    }

    /** Deletes the card and its bubble/photo crop, if it has one. */
    suspend fun delete(id: Long) {
        val imagePath = dao.get(id)?.imagePath
        dao.delete(id)
        imagePath?.let { File(it).delete() }
    }

    /** Folder for card images (crops from the camera or the reader). */
    fun imagesDir(): File = File(context.filesDir, "card-images").apply { mkdirs() }
}

/** Reader preferences (what is drawn on top of the manga). */
data class ReaderSettings(
    val showFuriganaOnPage: Boolean = true,
    val showStatusDot: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showOcrBoxes: Boolean = true,
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
        val OCR_BOXES = booleanPreferencesKey("ocr_boxes")
        val SEEDED = booleanPreferencesKey("seeded_samples")
    }

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            showFuriganaOnPage = prefs[Keys.FURIGANA_ON_PAGE] ?: true,
            showStatusDot = prefs[Keys.STATUS_DOT] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            showOcrBoxes = prefs[Keys.OCR_BOXES] ?: true,
        )
    }

    suspend fun setShowFuriganaOnPage(value: Boolean) = set(Keys.FURIGANA_ON_PAGE, value)
    suspend fun setShowStatusDot(value: Boolean) = set(Keys.STATUS_DOT, value)
    suspend fun setKeepScreenOn(value: Boolean) = set(Keys.KEEP_SCREEN_ON, value)
    suspend fun setShowOcrBoxes(value: Boolean) = set(Keys.OCR_BOXES, value)

    suspend fun isSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false
    suspend fun markSeeded() = set(Keys.SEEDED, true)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
