package io.github.otoniel2004.mangareaderjp.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
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

@Database(entities = [SavedItem::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedItemDao(): SavedItemDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "manga-reader.db").build()
    }
}
