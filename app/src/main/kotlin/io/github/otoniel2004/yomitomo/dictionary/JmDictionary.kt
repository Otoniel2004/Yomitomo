package io.github.otoniel2004.yomitomo.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * JMdict on the phone.
 *
 * The database ships inside the APK as an asset, but SQLite can only open real files, so on
 * first use it is copied to app storage (once per dictionary version, ~1 s). After that every
 * lookup is an indexed SQLite query: instant and offline.
 *
 * Dictionary data: JMdict by the Electronic Dictionary Research and Development Group,
 * CC BY-SA 4.0 (https://www.edrdg.org/edrdg/licence.html).
 */
class JmDictionary(context: Context) : Dictionary {
    private val appContext = context.applicationContext
    @Volatile private var database: SQLiteDatabase? = null

    /** Copies/opens the database. Safe to call many times; call it early off the main thread. */
    @Synchronized
    fun open(): SQLiteDatabase {
        database?.let { return it }
        val version = appContext.assets.open("$ASSET_DIR/jmdict.version").bufferedReader().use { it.readText().trim() }
        val dir = File(appContext.noBackupFilesDir, "dictionary").apply { mkdirs() }
        val target = File(dir, "jmdict-$version.db")
        if (!target.exists()) {
            dir.listFiles()?.forEach { it.delete() } // drop older versions
            val temp = File(dir, "jmdict.tmp")
            appContext.assets.open("$ASSET_DIR/jmdict.db").use { input ->
                temp.outputStream().use { input.copyTo(it, 1 shl 16) }
            }
            check(temp.renameTo(target)) { "Could not install the dictionary" }
        }
        return SQLiteDatabase.openDatabase(
            target.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        ).also { database = it }
    }

    override fun byReading(hiragana: String, limit: Int): List<DictEntry> =
        entries("SELECT DISTINCT entry_id FROM reading WHERE norm = ?", hiragana, limit)

    override fun byText(text: String, limit: Int): List<DictEntry> = entries(
        "SELECT entry_id FROM kanji WHERE text = ?1 UNION SELECT entry_id FROM reading WHERE text = ?1",
        text, limit,
    )

    private fun entries(idQuery: String, argument: String, limit: Int): List<DictEntry> {
        val db = open()
        val ids = db.rawQuery(
            "SELECT id FROM entry WHERE id IN ($idQuery) ORDER BY priority, id LIMIT $limit",
            arrayOf(argument),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        if (ids.isEmpty()) return emptyList()

        val idList = ids.joinToString(",")
        val header = mutableMapOf<Long, Triple<Int, Boolean, Int>>()
        db.rawQuery("SELECT id, priority, usually_kana, jlpt FROM entry WHERE id IN ($idList)", null).use { c ->
            while (c.moveToNext()) header[c.getLong(0)] = Triple(c.getInt(1), c.getInt(2) == 1, c.getInt(3))
        }
        val kanji = texts(db, "SELECT entry_id, text FROM kanji WHERE entry_id IN ($idList) ORDER BY priority, rowid")
        val readings = texts(db, "SELECT entry_id, text FROM reading WHERE entry_id IN ($idList) ORDER BY priority, rowid")
        val senses = mutableMapOf<Long, MutableList<DictSense>>()
        db.rawQuery("SELECT entry_id, pos, misc, gloss FROM sense WHERE entry_id IN ($idList) ORDER BY entry_id, ord", null).use { c ->
            while (c.moveToNext()) {
                senses.getOrPut(c.getLong(0)) { mutableListOf() } +=
                    DictSense(split(c.getString(1)), split(c.getString(2)), split(c.getString(3)))
            }
        }
        return ids.mapNotNull { id ->
            val (priority, usuallyKana, jlpt) = header[id] ?: return@mapNotNull null
            DictEntry(id, kanji[id].orEmpty(), readings[id].orEmpty(), senses[id].orEmpty(), priority, usuallyKana, jlpt)
        }
    }

    private fun texts(db: SQLiteDatabase, sql: String): Map<Long, List<String>> {
        val out = mutableMapOf<Long, MutableList<String>>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) out.getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1)
        }
        return out
    }

    private fun split(value: String) = if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val ASSET_DIR = "dictionary"
        const val SEPARATOR = "\u001F"
    }
}
