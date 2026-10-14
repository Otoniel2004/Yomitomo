package io.github.otoniel2004.yomitomo.japanese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiToKanaTest {
    private fun hira(romaji: String) = RomajiToKana.toHiragana(romaji)

    @Test
    fun `basic and combined syllables`() {
        assertEquals("やくそく", hira("yakusoku"))
        assertEquals("しゃしん", hira("shashin"))
        assertEquals("りょこう", hira("ryokou"))
        assertEquals("ちゃ", hira("cha"))
        assertEquals("じゅう", hira("juu"))
    }

    @Test
    fun `alternative spellings`() {
        assertEquals("し", hira("si"))
        assertEquals("ち", hira("ti"))
        assertEquals("つ", hira("tu"))
        assertEquals("ふ", hira("hu"))
    }

    @Test
    fun `double consonants become small tsu`() {
        assertEquals("まって", hira("matte"))
        assertEquals("きっと", hira("kitto"))
        assertEquals("まっち", hira("matchi"))
        assertEquals("ざっし", hira("zasshi"))
    }

    @Test
    fun `n rules`() {
        assertEquals("こんにちわ", hira("konnichiwa"))
        assertEquals("さんぽ", hira("sanpo"))
        assertEquals("きんえん", hira("kin'en"))
        assertEquals("ほん", hira("hon"))
        assertEquals("にほんご", hira("nihongo"))
        assertEquals("こんや", hira("kon'ya"))
    }

    @Test
    fun `long vowel mark and punctuation`() {
        assertEquals("こーひー", hira("ko-hi-"))
        assertEquals("ほんと？", hira("honto?"))
    }

    @Test
    fun `capitalized words become katakana`() {
        assertEquals("コーヒー を のむ", RomajiToKana.convert("KO-HI- wo nomu"))
        assertEquals("ふぁん", RomajiToKana.convert("fan"))
        assertEquals("ファン", RomajiToKana.convert("FAN"))
    }

    @Test
    fun `japanese text passes through`() {
        assertEquals("約束です", hira("約束desu"))
        assertTrue(RomajiToKana.looksLikeRomaji("yakusoku"))
        assertFalse(RomajiToKana.looksLikeRomaji("約束"))
    }
}
