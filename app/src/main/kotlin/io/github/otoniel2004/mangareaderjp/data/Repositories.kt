package io.github.otoniel2004.mangareaderjp.data

import android.content.Context
import java.io.File
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.otoniel2004.mangareaderjp.srs.Rating
import io.github.otoniel2004.mangareaderjp.srs.Srs
import io.github.otoniel2004.mangareaderjp.srs.ReviewEvent
import io.github.otoniel2004.mangareaderjp.srs.StudyStats
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SavedItemRepository(
    private val dao: SavedItemDao,
    private val context: Context,
    private val reviewLog: ReviewLogDao,
) {
    fun observeAll(): Flow<List<SavedItem>> = dao.observeAll()

    fun observe(id: Long): Flow<SavedItem?> = dao.observe(id)

    suspend fun dueItems(now: Long): List<SavedItem> = dao.due(now)

    suspend fun insertAll(items: List<SavedItem>) = dao.insertAll(items)

    suspend fun insert(item: SavedItem): Long = dao.insert(item)

    suspend fun setShowFurigana(id: Long, show: Boolean) = dao.setShowFurigana(id, show)

    suspend fun review(item: SavedItem, rating: Rating, now: Long): SavedItem {
        val updated = item.copy(srs = Srs.schedule(item.srs, rating, now))
        dao.update(updated)
        reviewLog.insert(ReviewLogEntry(itemId = item.id, rating = rating, reviewedAt = now, intervalDays = updated.srs.intervalDays))
        return updated
    }

    /** Deletes the card and its bubble/photo crop, if it has one. */
    suspend fun delete(id: Long) {
        val imagePath = dao.get(id)?.imagePath
        dao.delete(id)
        imagePath?.let { File(it).delete() }
    }

    /** Words and expressions you know well (interval of 21+ days), as written on their cards. */
    suspend fun knownWordTexts(): Set<String> =
        dao.withIntervalAtLeast(StudyStats.KNOWN_INTERVAL_DAYS).filter { it.type != ItemType.SENTENCE }.map { it.text }.toSet()

    /** Everything the statistics screen needs. */
    suspend fun stats(now: Long): StudyStats = StudyStats.compute(
        cards = dao.getAll().map { it.srs },
        reviews = reviewLog.getAll().map { ReviewEvent(it.rating, it.reviewedAt) },
        now = now,
        zone = ZoneId.systemDefault(),
    )

    /** Folder for card images (crops from the camera or the reader). */
    fun imagesDir(): File = File(context.filesDir, "card-images").apply { mkdirs() }
}

/** Size of the furigana drawn on the page, relative to the characters. */
enum class FuriganaSize(val ratio: Double) { SMALL(0.36), MEDIUM(0.45), LARGE(0.56) }

/** Reader preferences (what is drawn on top of the manga). */
data class ReaderSettings(
    val showFuriganaOnPage: Boolean = true,
    val showStatusDot: Boolean = true,
    val keepScreenOn: Boolean = true,
    val showOcrBoxes: Boolean = false,
    /** Translate every new page right away (instant popups, uses DeepL quota) or only on request. */
    val autoTranslate: Boolean = true,
    val furiganaSize: FuriganaSize = FuriganaSize.MEDIUM,
    /** Checks the screen less often: slower to notice a new page, lighter on the battery. */
    val batterySaver: Boolean = false,
    /** No furigana over words you already know (cards with an interval of 21+ days). */
    val hideKnownFurigana: Boolean = false,
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
        val AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        val FURIGANA_SIZE = stringPreferencesKey("furigana_size")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val HIDE_KNOWN_FURIGANA = booleanPreferencesKey("hide_known_furigana")
        val SEEDED = booleanPreferencesKey("seeded_samples")
    }

    val readerSettings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            showFuriganaOnPage = prefs[Keys.FURIGANA_ON_PAGE] ?: true,
            showStatusDot = prefs[Keys.STATUS_DOT] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            showOcrBoxes = prefs[Keys.OCR_BOXES] ?: false,
            autoTranslate = prefs[Keys.AUTO_TRANSLATE] ?: true,
            furiganaSize = prefs[Keys.FURIGANA_SIZE]?.let { runCatching { FuriganaSize.valueOf(it) }.getOrNull() } ?: FuriganaSize.MEDIUM,
            batterySaver = prefs[Keys.BATTERY_SAVER] ?: false,
            hideKnownFurigana = prefs[Keys.HIDE_KNOWN_FURIGANA] ?: false,
        )
    }

    suspend fun setShowFuriganaOnPage(value: Boolean) = set(Keys.FURIGANA_ON_PAGE, value)
    suspend fun setShowStatusDot(value: Boolean) = set(Keys.STATUS_DOT, value)
    suspend fun setKeepScreenOn(value: Boolean) = set(Keys.KEEP_SCREEN_ON, value)
    suspend fun setShowOcrBoxes(value: Boolean) = set(Keys.OCR_BOXES, value)
    suspend fun setAutoTranslate(value: Boolean) = set(Keys.AUTO_TRANSLATE, value)
    suspend fun setBatterySaver(value: Boolean) = set(Keys.BATTERY_SAVER, value)
    suspend fun setHideKnownFurigana(value: Boolean) = set(Keys.HIDE_KNOWN_FURIGANA, value)
    suspend fun setFuriganaSize(value: FuriganaSize) {
        context.dataStore.edit { it[Keys.FURIGANA_SIZE] = value.name }
    }

    suspend fun isSeeded(): Boolean = context.dataStore.data.first()[Keys.SEEDED] ?: false
    suspend fun markSeeded() = set(Keys.SEEDED, true)

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
