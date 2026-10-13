package io.github.otoniel2004.mangareaderjp.japanese

import io.github.otoniel2004.mangareaderjp.japanese.Deinflector.WordClass
import org.junit.Assert.assertTrue
import org.junit.Test

class DeinflectorTest {
    private fun assertBase(word: String, base: String, wordClass: WordClass) {
        val results = Deinflector.deinflect(word)
        assertTrue(
            "$word should deinflect to $base ($wordClass), got $results",
            results.any { it.base == base && it.wordClass == wordClass },
        )
    }

    @Test
    fun `ichidan verbs`() {
        assertBase("あきらめない", "あきらめる", WordClass.ICHIDAN)
        assertBase("あきらめなかった", "あきらめる", WordClass.ICHIDAN)
        assertBase("たべました", "たべる", WordClass.ICHIDAN)
        assertBase("たべられる", "たべる", WordClass.ICHIDAN)
    }

    @Test
    fun `godan verbs`() {
        assertBase("かいた", "かく", WordClass.GODAN)
        assertBase("よんで", "よむ", WordClass.GODAN)
        assertBase("まって", "まつ", WordClass.GODAN)
        assertBase("はなします", "はなす", WordClass.GODAN)
        assertBase("いかない", "いく", WordClass.GODAN)
        assertBase("いった", "いく", WordClass.GODAN)
        assertBase("のもう", "のむ", WordClass.GODAN)
    }

    @Test
    fun `progressive and contracted forms`() {
        assertBase("まってる", "まつ", WordClass.GODAN)
        assertBase("たべている", "たべる", WordClass.ICHIDAN)
        assertBase("よんじゃった", "よむ", WordClass.GODAN)
    }

    @Test
    fun `suru and kuru`() {
        assertBase("べんきょうした", "べんきょう", WordClass.SURU)
        assertBase("きた", "くる", WordClass.KURU)
        assertBase("こなかった", "くる", WordClass.KURU)
    }

    @Test
    fun `i adjectives`() {
        assertBase("たかかった", "たかい", WordClass.ADJECTIVE_I)
        assertBase("よくない", "よい", WordClass.ADJECTIVE_I)
    }

    @Test
    fun `results never include the input itself`() {
        assertTrue(Deinflector.deinflect("たべる").none { it.base == "たべる" })
    }
}
