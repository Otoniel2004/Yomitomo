package io.github.otoniel2004.mangareaderjp.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsTest {
    private val now = 1_000_000_000L

    @Test
    fun `new card starts due`() {
        assertTrue(Srs.isDue(Srs.newCard(now), now))
    }

    @Test
    fun `successive goods grow the interval by ease`() {
        var s = Srs.newCard(now)
        s = Srs.schedule(s, Rating.GOOD, now)
        assertEquals(1, s.intervalDays)
        s = Srs.schedule(s, Rating.GOOD, now)
        assertEquals(3, s.intervalDays) // 1 × 2.5 = 2.5 → 3
        s = Srs.schedule(s, Rating.GOOD, now)
        assertEquals(8, s.intervalDays) // 3 × 2.5 = 7.5 → 8
        assertEquals(now + 8 * Srs.DAY, s.dueAt)
        assertEquals(3, s.repetitions)
    }

    @Test
    fun `easy on a new card jumps to 4 days`() {
        val s = Srs.schedule(Srs.newCard(now), Rating.EASY, now)
        assertEquals(4, s.intervalDays)
        assertEquals(2.65, s.easeFactor, 1e-9)
    }

    @Test
    fun `again returns in 10 minutes and counts a lapse only if known`() {
        val fresh = Srs.schedule(Srs.newCard(now), Rating.AGAIN, now)
        assertEquals(now + 10 * Srs.MINUTE, fresh.dueAt)
        assertEquals(0, fresh.lapses)

        val learned = Srs.schedule(Srs.newCard(now), Rating.GOOD, now)
        val forgot = Srs.schedule(learned, Rating.AGAIN, now)
        assertEquals(1, forgot.lapses)
        assertEquals(0, forgot.repetitions)
        assertEquals(2.3, forgot.easeFactor, 1e-9)
    }

    @Test
    fun `ease never drops below the minimum`() {
        var s = Srs.newCard(now)
        repeat(20) { s = Srs.schedule(s, Rating.AGAIN, now) }
        assertEquals(1.3, s.easeFactor, 1e-9)
    }

    @Test
    fun `hard grows at least 1 day`() {
        val s = SrsState(dueAt = now, intervalDays = 2, repetitions = 2)
        assertEquals(3, Srs.nextIntervalDays(s, Rating.HARD)) // 2 × 1.2 = 2.4 < 2 + 1
    }

    @Test
    fun `interval labels`() {
        assertEquals("1 day", Srs.formatDays(1))
        assertEquals("12 days", Srs.formatDays(12))
        assertEquals("2 months", Srs.formatDays(65))
        assertEquals("1 year", Srs.formatDays(400))
    }
}
