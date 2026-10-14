package io.github.otoniel2004.yomitomo.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class StudyStatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(day: Int, hour: Int = 12) = LocalDateTime.of(2026, 10, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val now = at(14, 20)

    @Test
    fun `counts cards, due and known`() {
        val cards = listOf(
            SrsState(dueAt = now - 1),
            SrsState(dueAt = now + Srs.DAY, intervalDays = 30),
            SrsState(dueAt = now + Srs.DAY, intervalDays = 3),
        )
        val stats = StudyStats.compute(cards, emptyList(), now, zone)
        assertEquals(3, stats.totalCards)
        assertEquals(1, stats.dueNow)
        assertEquals(1, stats.known)
        assertNull(stats.retention)
    }

    @Test
    fun `streak counts consecutive days ending today`() {
        val reviews = listOf(14, 13, 12, 10).map { ReviewEvent(Rating.GOOD, at(it)) }
        val stats = StudyStats.compute(emptyList(), reviews, now, zone)
        assertEquals(3, stats.streakDays)
        assertEquals(1, stats.reviewsToday)
    }

    @Test
    fun `streak survives until the end of today`() {
        // Studied yesterday and the day before, not yet today: the streak is still alive.
        val reviews = listOf(13, 12).map { ReviewEvent(Rating.GOOD, at(it)) }
        assertEquals(2, StudyStats.compute(emptyList(), reviews, now, zone).streakDays)
    }

    @Test
    fun `last seven days and retention`() {
        val reviews = listOf(
            ReviewEvent(Rating.GOOD, at(14)), ReviewEvent(Rating.AGAIN, at(14)),
            ReviewEvent(Rating.EASY, at(8)), ReviewEvent(Rating.HARD, at(1)),
        )
        val stats = StudyStats.compute(emptyList(), reviews, now, zone)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 2), stats.lastSevenDays)
        assertEquals(0.75, stats.retention!!, 1e-9)
    }
}
