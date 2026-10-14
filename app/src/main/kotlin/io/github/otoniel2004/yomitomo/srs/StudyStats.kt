package io.github.otoniel2004.yomitomo.srs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One review, as stored in the review log. */
data class ReviewEvent(val rating: Rating, val reviewedAt: Long)

/** What the statistics screen shows. */
data class StudyStats(
    val totalCards: Int,
    val dueNow: Int,
    /** Cards with an interval of 21+ days: you reliably remember them ("mature" in Anki). */
    val known: Int,
    val reviewsToday: Int,
    /** Consecutive days with at least one review, ending today (or yesterday, if today has none yet). */
    val streakDays: Int,
    /** Reviews per day for the last 7 days, oldest first; the last value is today. */
    val lastSevenDays: List<Int>,
    /** Share of reviews in the last 30 days that weren't "Again" (0..1), or null with no reviews. */
    val retention: Double?,
) {
    companion object {
        const val KNOWN_INTERVAL_DAYS = 21

        fun compute(cards: List<SrsState>, reviews: List<ReviewEvent>, now: Long, zone: ZoneId): StudyStats {
            val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
            val reviewDays = reviews.map { LocalDate.ofInstant(Instant.ofEpochMilli(it.reviewedAt), zone) }
            val perDay = reviewDays.groupingBy { it }.eachCount()

            var streak = 0
            var day = if (perDay.containsKey(today)) today else today.minusDays(1)
            while (perDay.containsKey(day)) {
                streak++
                day = day.minusDays(1)
            }

            val monthAgo = now - 30 * Srs.DAY
            val recent = reviews.filter { it.reviewedAt >= monthAgo }
            return StudyStats(
                totalCards = cards.size,
                dueNow = cards.count { Srs.isDue(it, now) },
                known = cards.count { it.intervalDays >= KNOWN_INTERVAL_DAYS },
                reviewsToday = perDay[today] ?: 0,
                streakDays = streak,
                lastSevenDays = (6 downTo 0).map { perDay[today.minusDays(it.toLong())] ?: 0 },
                retention = if (recent.isEmpty()) null else recent.count { it.rating != Rating.AGAIN }.toDouble() / recent.size,
            )
        }
    }
}
