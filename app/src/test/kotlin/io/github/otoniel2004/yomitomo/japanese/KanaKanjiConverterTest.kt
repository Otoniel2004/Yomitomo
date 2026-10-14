package io.github.otoniel2004.yomitomo.japanese

import io.github.otoniel2004.yomitomo.data.PartOfSpeech
import io.github.otoniel2004.yomitomo.dictionary.DictEntry
import io.github.otoniel2004.yomitomo.dictionary.DictSense
import io.github.otoniel2004.yomitomo.dictionary.Dictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A handful of JMdict-like entries, enough to exercise the converter. */
class FakeDictionary : Dictionary {
    private var nextId = 1L
    private fun entry(kanji: List<String>, reading: String, pos: List<String>, gloss: String, priority: Int = 10, uk: Boolean = false) =
        DictEntry(nextId++, kanji, listOf(reading), listOf(DictSense(pos, emptyList(), listOf(gloss))), priority, uk)

    private val entries = listOf(
        entry(listOf("俺"), "おれ", listOf("pn"), "I (male)"),
        entry(listOf("絶対"), "ぜったい", listOf("adv", "n"), "absolutely"),
        entry(listOf("諦める"), "あきらめる", listOf("v1", "vt"), "to give up"),
        entry(listOf("仕方がない"), "しかたがない", listOf("exp", "adj-i"), "it can't be helped"),
        entry(listOf("珈琲"), "コーヒー", listOf("n"), "coffee", uk = true),
        entry(listOf("橋"), "はし", listOf("n"), "bridge", priority = 5),
        entry(listOf("箸"), "はし", listOf("n"), "chopsticks", priority = 8),
        entry(listOf("勉強"), "べんきょう", listOf("n", "vs"), "study"),
        entry(listOf("行く"), "いく", listOf("v5k-s", "vi"), "to go"),
        entry(listOf("北"), "きた", listOf("n"), "north", priority = 12),
        entry(listOf("来る"), "くる", listOf("vk", "vi"), "to come", priority = 12),
        entry(listOf("食べる"), "たべる", listOf("v1", "vt"), "to eat"),
    )

    override fun byReading(hiragana: String, limit: Int) =
        entries.filter { e -> e.readings.any { JapaneseText.toHiragana(it) == hiragana } }.sortedBy { it.priority }.take(limit)

    override fun byText(text: String, limit: Int) =
        entries.filter { text in it.kanji || text in it.readings }.sortedBy { it.priority }.take(limit)
}

class KanaKanjiConverterTest {
    private val converter = KanaKanjiConverter(FakeDictionary())

    private fun surfaces(input: String) = converter.convert(input).map { it.choice.surface }

    @Test
    fun `sentence with particles and a conjugated verb`() {
        assertEquals(listOf("俺", "は", "絶対", "に", "諦めない"), surfaces("ore wa zettai ni akiramenai"))
    }

    @Test
    fun `punctuation becomes its own chunk`() {
        assertEquals(listOf("俺", "は", "絶対", "に", "諦めない", "！"), surfaces("ore wa zettai ni akiramenai!"))
    }

    @Test
    fun `dictionary phrase stays one expression`() {
        val chunks = converter.convert("shikata ga nai")
        assertEquals("仕方がない", chunks.single().choice.surface)
        assertTrue(chunks.single().choice.isExpression)
    }

    @Test
    fun `usually-kana loanword keeps katakana`() {
        assertEquals(listOf("コーヒー"), surfaces("koohii"))
        assertEquals(listOf("coffee"), converter.convert("koohii").single().choice.meanings)
    }

    @Test
    fun `ambiguous reading offers every option, most common first`() {
        val options = converter.convert("hashi").single().candidates.map { it.surface }
        assertEquals(listOf("橋", "箸", "はし", "ハシ"), options)
    }

    @Test
    fun `conjugated forms get their kanji back`() {
        assertEquals(listOf("勉強した"), surfaces("benkyoushita"))
        assertEquals(listOf("行った"), surfaces("itta"))
        assertEquals(listOf("食べました"), surfaces("tabemashita"))
        assertTrue(converter.convert("kita").single().candidates.any { it.surface == "来た" })
    }

    @Test
    fun `particle typed glued to a word is split off`() {
        assertEquals(listOf("俺", "は"), surfaces("orewa"))
    }

    @Test
    fun `capitals ask for katakana`() {
        assertEquals("ハシ", converter.convert("HASHI").single().choice.surface)
    }

    @Test
    fun `unknown words stay in kana`() {
        val chunk = converter.convert("zuzuzu").single()
        assertEquals("ずずず", chunk.choice.surface)
        assertEquals(PartOfSpeech.OTHER, chunk.choice.pos)
    }

    @Test
    fun `reinflect keeps the kanji stem`() {
        assertEquals("諦めない", KanaKanjiConverter.reinflect("諦める", "あきらめる", "あきらめない"))
        assertEquals("勉強した", KanaKanjiConverter.reinflect("勉強", "べんきょう", "べんきょうした"))
    }
}
