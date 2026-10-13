package io.github.otoniel2004.mangareaderjp.srs

import kotlin.math.ceil
import kotlin.math.max

/** Review buttons, as in Anki. */
enum class Rating { AGAIN, HARD, GOOD, EASY }

/**
 * Spaced repetition state of a card.
 *
 * - dueAt: when the card comes back for review (epoch ms)
 * - intervalDays: current interval in days (0 = still learning)
 * - easeFactor: how "easy" the card is; multiplies the interval on each success
 * - repetitions: successful reviews in a row since the last failure
 * - lapses: how many times a known card was forgotten
 */
data class SrsState(
    val dueAt: Long,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val lastReviewedAt: Long? = null,
)

/**
 * Simplified SM-2 scheduler (Anki's classic algorithm):
 *
 *   Again → back in 10 min, streak reset, card gets slightly "harder"
 *   Hard  → interval × 1.2 (ease −0.15)
 *   Good  → interval × ease
 *   Easy  → interval × ease × 1.3 (ease +0.15)
 *
 * Every success grows the interval by at least 1 day.
 */
object Srs {
    const val MINUTE = 60_000L
    const val DAY = 86_400_000L

    private const val AGAIN_DELAY = 10 * MINUTE
    private const val MIN_EASE = 1.3
    private const val MAX_INTERVAL_DAYS = 36_500

    fun newCard(now: Long) = SrsState(dueAt = now)

    fun isDue(state: SrsState, now: Long) = state.dueAt <= now

    fun schedule(state: SrsState, rating: Rating, now: Long): SrsState {
        if (rating == Rating.AGAIN) {
            return state.copy(
                dueAt = now + AGAIN_DELAY,
                intervalDays = 0,
                easeFactor = max(MIN_EASE, state.easeFactor - 0.2),
                repetitions = 0,
                lapses = state.lapses + if (state.repetitions > 0) 1 else 0,
                lastReviewedAt = now,
            )
        }
        val interval = nextIntervalDays(state, rating)
        val ease = when (rating) {
            Rating.HARD -> state.easeFactor - 0.15
            Rating.EASY -> state.easeFactor + 0.15
            else -> state.easeFactor
        }
        return state.copy(
            dueAt = now + interval * DAY,
            intervalDays = interval,
            easeFactor = max(MIN_EASE, ease),
            repetitions = state.repetitions + 1,
            lastReviewedAt = now,
        )
    }

    /** Interval (in days) the card would get with this rating. Not meant for AGAIN. */
    fun nextIntervalDays(state: SrsState, rating: Rating): Int {
        val raw = if (state.repetitions == 0) {
            if (rating == Rating.EASY) 4.0 else 1.0
        } else {
            val previous = state.intervalDays.coerceAtLeast(1)
            val grown = when (rating) {
                Rating.HARD -> previous * 1.2
                Rating.EASY -> previous * state.easeFactor * 1.3
                else -> previous * state.easeFactor
            }
            max(grown, previous + 1.0)
        }
        return ceil(raw).toInt().coerceIn(1, MAX_INTERVAL_DAYS)
    }

    /** Label shown under each button: "10 min", "1 day", "3 months"... */
    fun previewLabel(state: SrsState, rating: Rating): String =
        if (rating == Rating.AGAIN) "10 min" else formatDays(nextIntervalDays(state, rating))

    fun formatDays(days: Int): String = when {
        days < 30 -> if (days == 1) "1 day" else "$days days"
        days < 365 -> (days / 30).let { if (it == 1) "1 month" else "$it months" }
        else -> (days / 365).let { if (it == 1) "1 year" else "$it years" }
    }
}
