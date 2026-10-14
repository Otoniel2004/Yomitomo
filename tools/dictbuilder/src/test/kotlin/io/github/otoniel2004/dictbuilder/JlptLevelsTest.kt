package io.github.otoniel2004.dictbuilder

import org.junit.Assert.assertEquals
import org.junit.Test

class JlptLevelsTest {
    private val levels = JlptLevels(
        byWordAndReading = mapOf("会う|あう" to 5, "諦める|あきらめる" to 3),
        byWord = mapOf("ああ" to 5),
    )

    private fun entry(kanji: String?, reading: String) = Entry(
        id = 1,
        kanji = listOfNotNull(kanji).map { Element(it, 10) },
        readings = listOf(Element(reading, 10)),
        senses = listOf(Sense(listOf("v1"), emptyList(), listOf("x"))),
    )

    @Test
    fun `matches by spelling and reading`() {
        assertEquals(5, levels.levelOf(entry("会う", "あう")))
        assertEquals(3, levels.levelOf(entry("諦める", "あきらめる")))
    }

    @Test
    fun `same spelling with another reading is not a match`() {
        assertEquals(0, levels.levelOf(entry("会う", "かいう")))
    }

    @Test
    fun `kana words match by spelling`() {
        assertEquals(5, levels.levelOf(entry(null, "ああ")))
    }

    @Test
    fun `csv fields may be quoted and contain commas`() {
        assertEquals(listOf("会う", "あう", "to meet, to see", "JLPT"), JlptLevels.parseCsvLine("会う,あう,\"to meet, to see\",JLPT"))
        assertEquals(listOf("a\"b", "c"), JlptLevels.parseCsvLine("\"a\"\"b\",c"))
    }
}
