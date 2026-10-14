package io.github.otoniel2004.yomitomo.japanese

import io.github.otoniel2004.yomitomo.data.PartOfSpeech.ADVERB
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.AUXILIARY
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.NOUN
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PARTICLE
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PRONOUN
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.PUNCTUATION
import io.github.otoniel2004.yomitomo.data.PartOfSpeech.VERB
import io.github.otoniel2004.yomitomo.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class RomajiTest {
    @Test
    fun `simple and combined syllables`() {
        assertEquals("yakusoku", Romaji.fromKana("やくそく"))
        assertEquals("shashin", Romaji.fromKana("しゃしん"))
        assertEquals("ryokou", Romaji.fromKana("りょこう"))
    }

    @Test
    fun `katakana and long vowels`() {
        assertEquals("koohii", Romaji.fromKana("コーヒー"))
        assertEquals("fan", Romaji.fromKana("ファン"))
        assertEquals("paatii", Romaji.fromKana("パーティー"))
    }

    @Test
    fun `small tsu doubles the consonant`() {
        assertEquals("matte", Romaji.fromKana("まって"))
        assertEquals("matchi", Romaji.fromKana("マッチ"))
        assertEquals("kitto", Romaji.fromKana("きっと"))
    }

    @Test
    fun `n before a vowel gets an apostrophe`() {
        assertEquals("kin'en", Romaji.fromKana("きんえん"))
        assertEquals("hon'ya", Romaji.fromKana("ほんや"))
        assertEquals("sanpo", Romaji.fromKana("さんぽ"))
    }

    @Test
    fun `sentence with particles and a conjugated verb`() {
        val sentence = listOf(
            Segment("明日", "あした", NOUN), Segment("、", null, PUNCTUATION),
            Segment("駅", "えき", NOUN), Segment("の", null, PARTICLE),
            Segment("前", "まえ", NOUN), Segment("で", null, PARTICLE),
            Segment("待っ", "まっ", VERB), Segment("て", null, PARTICLE),
            Segment("る", null, AUXILIARY), Segment("ね", null, PARTICLE),
            Segment("。", null, PUNCTUATION),
        )
        assertEquals("ashita, eki no mae de matteru ne.", Romaji.fromSegments(sentence))
    }

    @Test
    fun `copula is its own word after nouns but past da stays glued to verbs`() {
        val weather = listOf(
            Segment("今日", "きょう", NOUN), Segment("は", null, PARTICLE),
            Segment("天気", "てんき", NOUN), Segment("です", null, AUXILIARY),
            Segment("ね", null, PARTICLE),
        )
        assertEquals("kyou wa tenki desu ne", Romaji.fromSegments(weather))

        val read = listOf(Segment("読ん", "よん", VERB), Segment("だ", null, AUXILIARY))
        assertEquals("yonda", Romaji.fromSegments(read))
    }

    @Test
    fun `particle wa and glued auxiliary`() {
        val sentence = listOf(
            Segment("俺", "おれ", PRONOUN), Segment("は", null, PARTICLE),
            Segment("絶対", "ぜったい", ADVERB), Segment("に", null, PARTICLE),
            Segment("諦め", "あきらめ", VERB), Segment("ない", null, AUXILIARY),
            Segment("！", null, PUNCTUATION),
        )
        assertEquals("ore wa zettai ni akiramenai!", Romaji.fromSegments(sentence))
    }
}
