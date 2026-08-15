package dev.ikna.domain.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    companion object {
        /**
         * The grade a stored number means, or null when it is not a grade.
         *
         * `reviews.rating` is 0 on a retraction row -- deliberately not a valid
         * FSRS grade, so a reader unaware of undo cannot mistake one for an
         * answer. Every query in the app filters those out, and anything that
         * reads the log row by row has to keep doing so: [of] throws on 0, and
         * this is the version to reach for when the rows have not been filtered
         * yet.
         */
        fun ofOrNull(v: Int): Rating? = entries.firstOrNull { it.value == v }

        fun of(v: Int): Rating = ofOrNull(v)
            ?: throw IllegalArgumentException(
                "$v is not a rating. 1..4 are grades; 0 is a retraction row and " +
                    "belongs to a query that filters retracted answers out."
            )
    }
}

data class MemoryState(val stability: Double, val difficulty: Double)

data class FsrsParams(
    val w: List<Double> = DEFAULT_W,
    val desiredRetention: Double = 0.9
) {
    companion object {
        // FSRS-4.5 defaults. Replaced later by locally optimised parameters
        // computed from the `reviews` log.
        val DEFAULT_W = listOf(
            0.4872, 1.4003, 3.7145, 13.8206,
            5.1618, 1.2298, 0.8975, 0.0310,
            1.6474, 0.1367, 1.0461, 2.1072,
            0.0793, 0.3246, 1.5870, 0.2272, 2.8755
        )
    }
}

/** FSRS-4.5. Item level only; the component layer never feeds back into it. */
object Fsrs {

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val MIN_STABILITY = 0.1
    private const val MAX_STABILITY = 36500.0

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * elapsedDays / max(stability, MIN_STABILITY)).pow(DECAY)

    /**
     * Days until the card should come back, at the desired retention.
     *
     * The floor of one full day is load-bearing, not cosmetic. A card being
     * learned is already shown again inside the same session -- anything rated
     * "again" goes back into the queue -- so a sub-day interval would add a
     * second appearance the same evening that the session provides anyway, and
     * would put a card into tomorrow's due query for a lapse that has already
     * been dealt with. It is also what makes day-boundary snapping in
     * [Scheduler] unable to move a due time into the past.
     */
    fun intervalDays(stability: Double, desiredRetention: Double): Double =
        max(1.0, stability / FACTOR * (desiredRetention.pow(1.0 / DECAY) - 1.0))

    fun initialStability(rating: Rating, p: FsrsParams): Double =
        clampStability(p.w[rating.value - 1])

    fun initialDifficulty(rating: Rating, p: FsrsParams): Double =
        clampDifficulty(p.w[4] - (rating.value - 3) * p.w[5])

    fun initial(rating: Rating, p: FsrsParams): MemoryState =
        MemoryState(initialStability(rating, p), initialDifficulty(rating, p))

    fun next(
        state: MemoryState,
        elapsedDays: Double,
        rating: Rating,
        p: FsrsParams
    ): MemoryState {
        val r = retrievability(elapsedDays, state.stability)
        val d = nextDifficulty(state.difficulty, rating, p)
        val s = if (rating == Rating.AGAIN) {
            forgetStability(d, state.stability, r, p)
        } else {
            recallStability(d, state.stability, r, rating, p)
        }
        return MemoryState(clampStability(s), d)
    }

    private fun nextDifficulty(d: Double, rating: Rating, p: FsrsParams): Double {
        val delta = d - p.w[6] * (rating.value - 3)
        val reverted = p.w[7] * initialDifficulty(Rating.EASY, p) + (1.0 - p.w[7]) * delta
        return clampDifficulty(reverted)
    }

    private fun recallStability(
        d: Double,
        s: Double,
        r: Double,
        rating: Rating,
        p: FsrsParams
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) p.w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) p.w[16] else 1.0
        val growth = 1.0 +
            exp(p.w[8]) *
            (11.0 - d) *
            s.pow(-p.w[9]) *
            (exp((1.0 - r) * p.w[10]) - 1.0) *
            hardPenalty *
            easyBonus
        return s * growth
    }

    private fun forgetStability(d: Double, s: Double, r: Double, p: FsrsParams): Double {
        val lapsed = p.w[11] *
            d.pow(-p.w[12]) *
            ((s + 1.0).pow(p.w[13]) - 1.0) *
            exp((1.0 - r) * p.w[14])
        // A lapse can never make a memory stronger.
        return min(lapsed, s)
    }

    private fun clampStability(s: Double) = min(max(s, MIN_STABILITY), MAX_STABILITY)
    private fun clampDifficulty(d: Double) = min(max(d, 1.0), 10.0)
}
