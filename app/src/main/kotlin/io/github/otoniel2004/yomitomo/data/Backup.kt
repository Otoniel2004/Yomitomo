package io.github.otoniel2004.yomitomo.data

import io.github.otoniel2004.yomitomo.srs.SrsState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

/**
 * Backup file format (JSON). Everything a card needs, images included (as base64), so a
 * single file restores the whole collection on another phone.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val items: List<BackupItem>,
) {
    companion object {
        const val FORMAT = "yomitomo-cards"

        /** Backups made before the app was renamed (Yomitomo). */
        val LEGACY_FORMATS = setOf("manga-reader-jp-cards")
    }
}

@Serializable
data class BackupItem(
    val type: ItemType,
    val text: String,
    val segments: List<Segment>,
    val meanings: List<String> = emptyList(),
    val translation: String? = null,
    val contextSegments: List<Segment> = emptyList(),
    val contextTranslation: String? = null,
    val source: String? = null,
    val showFurigana: Boolean = true,
    val createdAt: Long,
    val dueAt: Long,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
    val imageJpegBase64: String? = null,
)

/**
 * Converts between cards and the backup format. Pure Kotlin (no Android types), so it is
 * unit-tested; reading/writing files is done by [BackupManager].
 */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == BackupFile.FORMAT || file.format in BackupFile.LEGACY_FORMATS) { "This is not a Yomitomo backup." }
        return file
    }

    fun toBackup(item: SavedItem, imageBytes: ByteArray?) = BackupItem(
        type = item.type,
        text = item.text,
        segments = item.segments,
        meanings = item.meanings,
        translation = item.translation,
        contextSegments = item.contextSegments,
        contextTranslation = item.contextTranslation,
        source = item.source,
        showFurigana = item.showFurigana,
        createdAt = item.createdAt,
        dueAt = item.srs.dueAt,
        intervalDays = item.srs.intervalDays,
        easeFactor = item.srs.easeFactor,
        repetitions = item.srs.repetitions,
        lapses = item.srs.lapses,
        lastReviewedAt = item.srs.lastReviewedAt,
        imageJpegBase64 = imageBytes?.let { Base64.getEncoder().encodeToString(it) },
    )

    fun toItem(backup: BackupItem, imagePath: String?) = SavedItem(
        type = backup.type,
        text = backup.text,
        segments = backup.segments,
        meanings = backup.meanings,
        translation = backup.translation,
        contextSegments = backup.contextSegments,
        contextTranslation = backup.contextTranslation,
        source = backup.source,
        imagePath = imagePath,
        showFurigana = backup.showFurigana,
        createdAt = backup.createdAt,
        srs = SrsState(
            dueAt = backup.dueAt,
            intervalDays = backup.intervalDays,
            easeFactor = backup.easeFactor,
            repetitions = backup.repetitions,
            lapses = backup.lapses,
            lastReviewedAt = backup.lastReviewedAt,
        ),
    )

    fun imageBytes(backup: BackupItem): ByteArray? =
        backup.imageJpegBase64?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

    /** Same card = same type, text and creation time (so importing twice adds nothing). */
    fun key(type: ItemType, text: String, createdAt: Long) = "$type|$text|$createdAt"
}

/** Writes and reads backup files, including the card images. */
class BackupManager(private val dao: SavedItemDao, private val imagesDir: () -> File) {

    suspend fun export(write: (String) -> Unit): Int {
        val items = dao.getAll()
        val backup = BackupFile(
            exportedAt = System.currentTimeMillis(),
            items = items.map { item ->
                val bytes = item.imagePath?.let { path -> File(path).takeIf { it.isFile }?.readBytes() }
                BackupCodec.toBackup(item, bytes)
            },
        )
        write(BackupCodec.encode(backup))
        return items.size
    }

    /** Adds the cards that aren't already here; returns how many were added. */
    suspend fun import(text: String): Int {
        val backup = BackupCodec.decode(text)
        val existing = dao.getAll().map { BackupCodec.key(it.type, it.text, it.createdAt) }.toMutableSet()
        var added = 0
        backup.items.forEachIndexed { index, entry ->
            if (!existing.add(BackupCodec.key(entry.type, entry.text, entry.createdAt))) return@forEachIndexed
            val imagePath = BackupCodec.imageBytes(entry)?.let { bytes ->
                File(imagesDir(), "import-${System.currentTimeMillis()}-$index.jpg").apply { writeBytes(bytes) }.path
            }
            dao.insert(BackupCodec.toItem(entry, imagePath))
            added++
        }
        return added
    }
}
