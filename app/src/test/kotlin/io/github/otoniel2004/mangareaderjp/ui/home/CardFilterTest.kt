package io.github.otoniel2004.mangareaderjp.ui.home

import io.github.otoniel2004.mangareaderjp.data.ItemType
import io.github.otoniel2004.mangareaderjp.data.PartOfSpeech
import io.github.otoniel2004.mangareaderjp.data.SavedItem
import io.github.otoniel2004.mangareaderjp.data.Segment
import io.github.otoniel2004.mangareaderjp.srs.SrsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFilterTest {
    private val sentence = SavedItem(
        type = ItemType.SENTENCE,
        text = "俺は絶対に諦めない",
        segments = listOf(
            Segment("俺", "おれ", PartOfSpeech.PRONOUN), Segment("は", null, PartOfSpeech.PARTICLE),
            Segment("絶対", "ぜったい", PartOfSpeech.ADVERB), Segment("に", null, PartOfSpeech.PARTICLE),
            Segment("諦め", "あきらめ", PartOfSpeech.VERB), Segment("ない", null, PartOfSpeech.AUXILIARY),
        ),
        translation = "I'm never giving up!",
        source = "Reader",
        createdAt = 0,
        srs = SrsState(dueAt = 0),
    )

    @Test
    fun `empty filter shows everything`() = assertTrue(CardFilter().matches(sentence))

    @Test
    fun `search by japanese, reading, romaji and english`() {
        assertTrue(CardFilter(query = "絶対").matches(sentence))
        assertTrue(CardFilter(query = "あきらめ").matches(sentence))
        assertTrue(CardFilter(query = "zettai").matches(sentence))
        assertTrue(CardFilter(query = "zettaini").matches(sentence))   // romaji typed without spaces
        assertTrue(CardFilter(query = "Giving Up").matches(sentence))  // case-insensitive
        assertTrue(CardFilter(query = "アキラメ").matches(sentence))    // katakana finds the hiragana reading
        assertFalse(CardFilter(query = "coffee").matches(sentence))
    }

    @Test
    fun `type and source filters`() {
        assertTrue(CardFilter(type = ItemType.SENTENCE, source = "Reader").matches(sentence))
        assertFalse(CardFilter(type = ItemType.WORD).matches(sentence))
        assertFalse(CardFilter(source = "Camera").matches(sentence))
    }
}
