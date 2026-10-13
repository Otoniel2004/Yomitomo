package io.github.otoniel2004.dictbuilder

import java.io.File
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate
import java.util.zip.GZIPInputStream

private const val JMDICT_URL = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"

/** Separator for list values stored in one column (never appears in dictionary text). */
const val SEPARATOR = "\u001F"

/**
 * Usage: dictbuilder <download-cache-dir> <asset-dir>
 *
 *   1. download JMdict_e.gz (once; kept in the cache dir)
 *   2. stream-parse the XML
 *   3. write asset-dir/jmdict.db (SQLite) and asset-dir/jmdict.version
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: dictbuilder <download-cache-dir> <asset-dir>" }
    val cacheDir = File(args[0]).apply { mkdirs() }
    val assetDir = File(args[1]).apply { mkdirs() }

    val source = File(cacheDir, "JMdict_e.gz")
    if (!source.exists()) {
        println("Downloading $JMDICT_URL")
        URI(JMDICT_URL).toURL().openStream().use { input -> source.outputStream().use { input.copyTo(it) } }
    }

    val output = File(assetDir, "jmdict.db")
    val temp = File(assetDir, "jmdict.db.tmp").apply { delete() }
    var count = 0
    DriverManager.getConnection("jdbc:sqlite:${temp.absolutePath}").use { db ->
        DictionaryWriter(db).use { writer ->
            GZIPInputStream(source.inputStream().buffered()).use { input ->
                JmdictParser.parse(input) { entry ->
                    writer.write(entry)
                    count++
                }
            }
        }
    }
    output.delete()
    check(temp.renameTo(output)) { "Could not move ${temp.name} to ${output.name}" }
    File(assetDir, "jmdict.version").writeText("${LocalDate.now()}-$count\n")
    println("Wrote $count entries to $output (${output.length() / 1_000_000} MB)")
}

/**
 * Database layout (read-only in the app):
 *
 *   entry(id, priority, usually_kana)
 *   kanji(entry_id, text, priority)
 *   reading(entry_id, text, norm, priority)   norm = reading in hiragana, used for lookups
 *   sense(entry_id, ord, pos, misc, gloss)    lists joined with SEPARATOR
 */
class DictionaryWriter(private val db: Connection) : AutoCloseable {
    init {
        db.createStatement().use { s ->
            s.executeUpdate("PRAGMA journal_mode = OFF")
            s.executeUpdate("PRAGMA synchronous = OFF")
            s.executeUpdate("CREATE TABLE entry(id INTEGER PRIMARY KEY, priority INTEGER NOT NULL, usually_kana INTEGER NOT NULL)")
            s.executeUpdate("CREATE TABLE kanji(entry_id INTEGER NOT NULL, text TEXT NOT NULL, priority INTEGER NOT NULL)")
            s.executeUpdate("CREATE TABLE reading(entry_id INTEGER NOT NULL, text TEXT NOT NULL, norm TEXT NOT NULL, priority INTEGER NOT NULL)")
            s.executeUpdate("CREATE TABLE sense(entry_id INTEGER NOT NULL, ord INTEGER NOT NULL, pos TEXT NOT NULL, misc TEXT NOT NULL, gloss TEXT NOT NULL)")
        }
        db.autoCommit = false
    }

    private val entryInsert = db.prepareStatement("INSERT INTO entry VALUES (?, ?, ?)")
    private val kanjiInsert = db.prepareStatement("INSERT INTO kanji VALUES (?, ?, ?)")
    private val readingInsert = db.prepareStatement("INSERT INTO reading VALUES (?, ?, ?, ?)")
    private val senseInsert = db.prepareStatement("INSERT INTO sense VALUES (?, ?, ?, ?, ?)")

    fun write(entry: Entry) {
        entryInsert.setLong(1, entry.id)
        entryInsert.setInt(2, entry.priority)
        entryInsert.setInt(3, if (entry.usuallyKana) 1 else 0)
        entryInsert.executeUpdate()
        for (k in entry.kanji) {
            kanjiInsert.setLong(1, entry.id)
            kanjiInsert.setString(2, k.text)
            kanjiInsert.setInt(3, k.priority)
            kanjiInsert.executeUpdate()
        }
        for (r in entry.readings) {
            readingInsert.setLong(1, entry.id)
            readingInsert.setString(2, r.text)
            readingInsert.setString(3, toHiragana(r.text))
            readingInsert.setInt(4, r.priority)
            readingInsert.executeUpdate()
        }
        entry.senses.forEachIndexed { index, sense ->
            senseInsert.setLong(1, entry.id)
            senseInsert.setInt(2, index)
            senseInsert.setString(3, sense.pos.joinToString(SEPARATOR))
            senseInsert.setString(4, sense.misc.joinToString(SEPARATOR))
            senseInsert.setString(5, sense.glosses.joinToString(SEPARATOR))
            senseInsert.executeUpdate()
        }
    }

    override fun close() {
        // Open statements block VACUUM, so close them first.
        listOf(entryInsert, kanjiInsert, readingInsert, senseInsert).forEach { it.close() }
        db.commit()
        db.createStatement().use { s ->
            s.executeUpdate("CREATE INDEX kanji_text ON kanji(text)")
            s.executeUpdate("CREATE INDEX kanji_entry ON kanji(entry_id)")
            s.executeUpdate("CREATE INDEX reading_norm ON reading(norm)")
            s.executeUpdate("CREATE INDEX reading_text ON reading(text)")
            s.executeUpdate("CREATE INDEX reading_entry ON reading(entry_id)")
            s.executeUpdate("CREATE INDEX sense_entry ON sense(entry_id)")
        }
        db.commit()
        db.autoCommit = true
        db.createStatement().use { it.executeUpdate("VACUUM") }
    }
}

/** Katakana → hiragana (the two Unicode blocks are 0x60 apart). */
fun toHiragana(text: String) = buildString(text.length) {
    for (c in text) append(if (c in 'ァ'..'ヶ') c - 0x60 else c)
}
