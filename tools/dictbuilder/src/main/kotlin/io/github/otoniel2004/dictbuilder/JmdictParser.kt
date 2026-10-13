package io.github.otoniel2004.dictbuilder

import java.io.InputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/** A kanji or reading element with its frequency rank (lower = more common). */
data class Element(val text: String, val priority: Int)

data class Sense(val pos: List<String>, val misc: List<String>, val glosses: List<String>)

data class Entry(val id: Long, val kanji: List<Element>, val readings: List<Element>, val senses: List<Sense>) {
    val priority get() = (kanji + readings).minOfOrNull { it.priority } ?: Priority.UNRANKED

    /** JMdict "uk" tag: the word is usually written in kana, even though it has kanji. */
    val usuallyKana get() = senses.firstOrNull()?.misc?.contains("uk") == true
}

object Priority {
    const val UNRANKED = 99

    /**
     * JMdict frequency tags → one number. nfXX is a rank in 500-word bands (nf01 = top 500);
     * news1/ichi1/spec1/gai1 mark common words, the "2" variants less common ones.
     */
    fun of(tags: List<String>): Int {
        var best = UNRANKED
        for (tag in tags) {
            val value = when {
                tag.startsWith("nf") -> tag.drop(2).toIntOrNull() ?: UNRANKED
                tag.endsWith("1") -> 10
                tag.endsWith("2") -> 30
                else -> UNRANKED
            }
            best = minOf(best, value)
        }
        return best
    }
}

/**
 * Streaming JMdict XML parser (StAX: reads the file element by element, never all in memory at once).
 *
 * JMdict writes part-of-speech and other tags as XML entities (&v1; &n; &uk;). We keep the
 * entity NAMES ("v1", "n", "uk") instead of their long English expansions.
 */
object JmdictParser {
    fun parse(input: InputStream, onEntry: (Entry) -> Unit) {
        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, true)
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
        }
        val reader = factory.createXMLStreamReader(input, "UTF-8")

        var id = 0L
        val kanji = mutableListOf<Element>()
        val readings = mutableListOf<Element>()
        val senses = mutableListOf<Sense>()

        var text = StringBuilder()
        var elementText = ""
        val elementTags = mutableListOf<String>()
        var pos = mutableListOf<String>()
        var misc = mutableListOf<String>()
        var glosses = mutableListOf<String>()
        var previousPos: List<String> = emptyList()

        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> {
                    text = StringBuilder()
                    when (reader.localName) {
                        "entry" -> {
                            kanji.clear(); readings.clear(); senses.clear()
                            previousPos = emptyList()
                        }
                        "k_ele", "r_ele" -> elementTags.clear()
                        "sense" -> {
                            pos = mutableListOf(); misc = mutableListOf(); glosses = mutableListOf()
                        }
                    }
                }
                XMLStreamConstants.CHARACTERS -> text.append(reader.text)
                XMLStreamConstants.ENTITY_REFERENCE -> text.append(reader.localName)
                XMLStreamConstants.END_ELEMENT -> {
                    val value = text.toString().trim()
                    when (reader.localName) {
                        "ent_seq" -> id = value.toLong()
                        "keb", "reb" -> elementText = value
                        "ke_pri", "re_pri" -> elementTags += value
                        "k_ele" -> kanji += Element(elementText, Priority.of(elementTags))
                        "r_ele" -> readings += Element(elementText, Priority.of(elementTags))
                        "pos" -> pos += value
                        "misc" -> misc += value
                        "gloss" -> if (value.isNotEmpty()) glosses += value
                        "sense" -> {
                            // A sense without <pos> uses the part of speech of the sense before it.
                            val effectivePos = pos.ifEmpty { previousPos }
                            previousPos = effectivePos
                            if (glosses.isNotEmpty()) senses += Sense(effectivePos, misc, glosses)
                        }
                        "entry" -> if (readings.isNotEmpty() && senses.isNotEmpty()) {
                            onEntry(Entry(id, kanji.toList(), readings.toList(), senses.toList()))
                        }
                    }
                    text = StringBuilder()
                }
            }
        }
        reader.close()
    }
}
