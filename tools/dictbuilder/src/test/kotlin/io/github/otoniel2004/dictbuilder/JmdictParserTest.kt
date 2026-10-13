package io.github.otoniel2004.dictbuilder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JmdictParserTest {
    // A tiny JMdict-shaped document: internal DTD with entities, two entries.
    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE JMdict [
        <!ELEMENT JMdict (entry*)>
        <!ENTITY v1 "Ichidan verb">
        <!ENTITY vt "transitive verb">
        <!ENTITY n "noun (common) (futsuumeishi)">
        <!ENTITY uk "word usually written using kana alone">
        ]>
        <JMdict>
        <entry>
        <ent_seq>1000001</ent_seq>
        <k_ele><keb>諦める</keb><ke_pri>ichi1</ke_pri><ke_pri>nf12</ke_pri></k_ele>
        <r_ele><reb>あきらめる</reb><re_pri>ichi1</re_pri></r_ele>
        <sense><pos>&v1;</pos><pos>&vt;</pos><gloss>to give up</gloss><gloss>to abandon</gloss></sense>
        <sense><gloss>to accept</gloss></sense>
        </entry>
        <entry>
        <ent_seq>1000002</ent_seq>
        <k_ele><keb>珈琲</keb></k_ele>
        <r_ele><reb>コーヒー</reb><re_pri>gai1</re_pri></r_ele>
        <sense><pos>&n;</pos><misc>&uk;</misc><gloss>coffee</gloss></sense>
        </entry>
        </JMdict>
    """.trimIndent()

    private fun parse(): List<Entry> {
        val entries = mutableListOf<Entry>()
        JmdictParser.parse(xml.byteInputStream()) { entries += it }
        return entries
    }

    @Test
    fun `entities are kept as tag names`() {
        val first = parse().first()
        assertEquals(listOf("v1", "vt"), first.senses[0].pos)
        assertEquals(listOf("to give up", "to abandon"), first.senses[0].glosses)
    }

    @Test
    fun `sense without pos inherits the previous one`() {
        assertEquals(listOf("v1", "vt"), parse().first().senses[1].pos)
    }

    @Test
    fun `priority and usually-kana flags`() {
        val (giveUp, coffee) = parse()
        assertEquals(1000001L, giveUp.id)
        assertEquals(10, giveUp.priority) // ichi1 beats nf12
        assertEquals(10, coffee.priority)
        assertTrue(coffee.usuallyKana)
    }

    @Test
    fun `priority tags`() {
        assertEquals(3, Priority.of(listOf("news1", "nf03")))
        assertEquals(30, Priority.of(listOf("spec2")))
        assertEquals(Priority.UNRANKED, Priority.of(emptyList()))
    }

    @Test
    fun `katakana readings are normalized to hiragana`() {
        assertEquals("こーひー", toHiragana("コーヒー"))
    }
}
