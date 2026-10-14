package io.github.otoniel2004.dictbuilder

import java.io.File
import java.net.URI

/**
 * JLPT vocabulary levels (N5 = beginner … N1 = advanced), from the open lists at
 * https://github.com/jamsinclair/open-anki-jlpt-decks (MIT), based on Jonathan Waller's
 * lists at tanos.co.uk (CC BY).
 *
 * A word may appear in several lists; the easiest level (highest N) wins.
 */
class JlptLevels(private val byWordAndReading: Map<String, Int>, private val byWord: Map<String, Int>) {

    /** Level of a dictionary entry (5..1), or 0 when it isn't in any JLPT list. */
    fun levelOf(entry: Entry): Int {
        val words = entry.kanji.map { it.text } + entry.readings.map { it.text }
        val readings = entry.readings.map { toHiragana(it.text) }
        for (word in words) {
            for (reading in readings) byWordAndReading[key(word, reading)]?.let { return it }
        }
        // Kana-only list words match by spelling alone.
        return words.firstNotNullOfOrNull { byWord[it] } ?: 0
    }

    companion object {
        private const val BASE = "https://raw.githubusercontent.com/jamsinclair/open-anki-jlpt-decks/main/src"

        fun load(cacheDir: File): JlptLevels {
            val byWordAndReading = mutableMapOf<String, Int>()
            val byWord = mutableMapOf<String, Int>()
            for (level in 5 downTo 1) {
                val file = File(cacheDir, "jlpt-n$level.csv")
                if (!file.exists()) {
                    println("Downloading JLPT N$level list")
                    URI("$BASE/n$level.csv").toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                }
                file.readLines().drop(1).forEach { line ->
                    val fields = parseCsvLine(line)
                    val word = fields.getOrNull(0)?.trim().orEmpty()
                    val reading = toHiragana(fields.getOrNull(1)?.trim().orEmpty())
                    if (word.isEmpty()) return@forEach
                    // Easier levels are read first and kept (putIfAbsent).
                    byWordAndReading.putIfAbsent(key(word, reading.ifEmpty { toHiragana(word) }), level)
                    if (word.all { it in '぀'..'ヿ' }) byWord.putIfAbsent(word, level)
                }
            }
            return JlptLevels(byWordAndReading, byWord)
        }

        private fun key(word: String, reading: String) = "$word|$reading"

        /** Minimal CSV: commas separate fields, double quotes may wrap a field, "" is a quote. */
        fun parseCsvLine(line: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                    c == '"' -> quoted = !quoted
                    c == ',' && !quoted -> { fields += current.toString(); current.clear() }
                    else -> current.append(c)
                }
                i++
            }
            fields += current.toString()
            return fields
        }
    }
}
