package io.github.otoniel2004.yomitomo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import io.github.otoniel2004.yomitomo.srs.Rating
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Dao
interface SavedItemDao {
    @Query("SELECT * FROM saved_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    fun observe(id: Long): Flow<SavedItem?>

    @Query("SELECT * FROM saved_items WHERE id = :id")
    suspend fun get(id: Long): SavedItem?

    @Query("SELECT * FROM saved_items ORDER BY createdAt")
    suspend fun getAll(): List<SavedItem>

    @Query("SELECT * FROM saved_items WHERE intervalDays >= :days")
    suspend fun withIntervalAtLeast(days: Int): List<SavedItem>

    @Query("SELECT * FROM saved_items WHERE dueAt <= :now ORDER BY dueAt")
    suspend fun due(now: Long): List<SavedItem>

    @Insert
    suspend fun insert(item: SavedItem): Long

    @Insert
    suspend fun insertAll(items: List<SavedItem>)

    @Update
    suspend fun update(item: SavedItem)

    @Query("UPDATE saved_items SET showFurigana = :show WHERE id = :id")
    suspend fun setShowFurigana(id: Long, show: Boolean)

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun delete(id: Long)
}

/** SQLite only stores simple types; lists are written as JSON text and parsed back on read. */
class Converters {
    // Explicit types: KSP (Room's code generator) can't see the serializer() the serialization plugin creates.
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val segments: KSerializer<List<Segment>> = ListSerializer(Segment.serializer())
    private val strings: KSerializer<List<String>> = ListSerializer(String.serializer())

    @TypeConverter
    fun segmentsToJson(value: List<Segment>): String = json.encodeToString(segments, value)

    @TypeConverter
    fun segmentsFromJson(value: String): List<Segment> = json.decodeFromString(segments, value)

    @TypeConverter
    fun stringsToJson(value: List<String>): String = json.encodeToString(strings, value)

    @TypeConverter
    fun stringsFromJson(value: String): List<String> = json.decodeFromString(strings, value)
}

/** A DeepL translation already paid for. */
@Entity(tableName = "translation_cache")
data class TranslationCacheEntry(
    @PrimaryKey val source: String,
    val translation: String,
    val createdAt: Long,
)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE source IN (:sources)")
    suspend fun get(sources: List<String>): List<TranslationCacheEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entries: List<TranslationCacheEntry>)
}

/** One study review, kept for the statistics screen. */
@Entity(tableName = "review_log")
data class ReviewLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val rating: Rating,
    val reviewedAt: Long,
    val intervalDays: Int,
)

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(entry: ReviewLogEntry)

    @Query("SELECT * FROM review_log ORDER BY reviewedAt")
    suspend fun getAll(): List<ReviewLogEntry>
}

/** Version 2 adds the translation cache; saved cards are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `translation_cache` (" +
                "`source` TEXT NOT NULL, `translation` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`source`))",
        )
    }
}

/** Version 3 adds the review log (statistics); existing data is untouched. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `itemId` INTEGER NOT NULL, " +
                "`rating` TEXT NOT NULL, `reviewedAt` INTEGER NOT NULL, `intervalDays` INTEGER NOT NULL)",
        )
    }
}

@Database(
    entities = [SavedItem::class, TranslationCacheEntry::class, ReviewLogEntry::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedItemDao(): SavedItemDao
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "yomitomo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
