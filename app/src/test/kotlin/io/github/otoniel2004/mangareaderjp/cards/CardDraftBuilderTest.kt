package io.github.otoniel2004.mangareaderjp.cards

import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.japanese.FakeDictionary
import io.github.otoniel2004.mangareaderjp.japanese.JapaneseAnalyzer
import io.github.otoniel2004.mangareaderjp.japanese.KanaKanjiConverter
import io.github.otoniel2004.mangareaderjp.japanese.Romaji
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uses the real Kuromoji tokenizer (it runs on the JVM too) with the fake dictionary. */
class CardDraftBuilderTest {
    private val dictionary = FakeDictionary()
    private val builder = CardDraftBuilder(dictionary, analyzer)
    private val converter = KanaKanjiConverter(dictionary)

    companion object {
        private val analyzer = JapaneseAnalyzer() // slow to build: share it across tests
    }

    @Test
    fun `romaji sentence becomes a sentence card with readings`() {
        val draft = builder.fromChunks(converter.convert("ore wa zettai ni akiramenai"))
        assertEquals("俺は絶対に諦めない", draft.text)
        assertEquals(ItemType.SENTENCE, draft.type)
        assertEquals("あきらめない", draft.segments.last().reading)
        assertEquals(null, draft.segments[1].reading) // は needs no furigana
        assertEquals("ore wa zettai ni akiramenai", Romaji.fromSegments(draft.segments))
    }

    @Test
    fun `romaji word becomes a word card with meanings`() {
        val draft = builder.fromChunks(converter.convert("koohii"))
        assertEquals(ItemType.WORD, draft.type)
        assertEquals("こーひー", draft.segments.single().reading)
        assertEquals(listOf("coffee"), draft.meanings)
    }

    @Test
    fun `romaji expression becomes an expression card`() {
        assertEquals(ItemType.EXPRESSION, builder.fromChunks(converter.convert("shikata ga nai")).type)
    }

    @Test
    fun `japanese sentence is tokenized with particles`() {
        val draft = builder.fromJapanese("俺は絶対に諦めない！")
        assertEquals(ItemType.SENTENCE, draft.type)
        assertEquals(PartOfSpeech.PARTICLE, draft.segments.first { it.surface == "は" }.pos)
        assertEquals("おれ", draft.segments.first().reading)
        assertEquals("ore wa zettai ni akiramenai!", Romaji.fromSegments(draft.segments))
    }

    @Test
    fun `full-width symbols are punctuation and unknown katakana gets a reading`() {
        val segments = builder.fromJapanese("ゼブラック大賞！").segments
        assertEquals(PartOfSpeech.PUNCTUATION, segments.last().pos)
        val katakana = segments.first { it.surface.startsWith("ゼ") }
        assertEquals("ぜぶらっく", katakana.reading?.take(5))
        assertEquals(true, JapaneseAnalyzer.isPunctuation("！？"))
        assertEquals(false, JapaneseAnalyzer.isPunctuation("大賞"))
    }

    @Test
    fun `japanese single word, even conjugated, is a word card`() {
        assertEquals(ItemType.WORD, builder.fromJapanese("勉強").type)
        val conjugated = builder.fromJapanese("食べなかった")
        assertEquals(ItemType.WORD, conjugated.type)
        assertEquals(listOf("to eat"), conjugated.meanings)
    }
}
