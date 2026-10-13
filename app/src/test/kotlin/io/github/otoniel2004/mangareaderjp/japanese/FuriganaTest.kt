package io.github.otoniel2004.mangareaderjp.japanese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaTest {
    @Test
    fun `katakana becomes hiragana`() {
        assertEquals("こーひー", JapaneseText.toHiragana("コーヒー"))
        assertEquals("まじで", JapaneseText.toHiragana("マジで"))
    }

    @Test
    fun `only kanji and katakana need furigana`() {
        assertTrue(JapaneseText.needsFurigana("約束"))
        assertTrue(JapaneseText.needsFurigana("コーヒー"))
        assertFalse(JapaneseText.needsFurigana("ありがとう"))
        assertFalse(JapaneseText.needsFurigana("！"))
    }

    @Test
    fun `okurigana stays outside the ruby`() {
        assertEquals(
            listOf(RubyPart("食", "た"), RubyPart("べる", null)),
            rubyParts("食べる", "たべる"),
        )
        assertEquals(
            listOf(RubyPart("諦", "あきら"), RubyPart("め", null)),
            rubyParts("諦め", "あきらめ"),
        )
    }

    @Test
    fun `kana prefix stays outside the ruby`() {
        assertEquals(
            listOf(RubyPart("お", null), RubyPart("茶", "ちゃ")),
            rubyParts("お茶", "おちゃ"),
        )
    }

    @Test
    fun `katakana gets the whole reading`() {
        assertEquals(listOf(RubyPart("コーヒー", "こーひー")), rubyParts("コーヒー", "こーひー"))
        assertEquals(
            listOf(RubyPart("ハマ", "はま"), RubyPart("った", null)),
            rubyParts("ハマった", "はまった"),
        )
    }

    @Test
    fun `no reading or plain hiragana has no ruby`() {
        assertEquals(listOf(RubyPart("約束", null)), rubyParts("約束", null))
        assertEquals(listOf(RubyPart("ない", null)), rubyParts("ない", "ない"))
    }

    @Test
    fun `mismatched reading produces no wrong ruby`() {
        assertEquals(listOf(RubyPart("見る", null)), rubyParts("見る", "る"))
    }
}
