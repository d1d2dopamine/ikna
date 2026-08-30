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
    init {
        require(w.size == PARAMETER_COUNT) {
            "FSRS-6 needs $PARAMETER_COUNT parameters, got ${w.size}"
        }
    }

    companion object {
        const val PARAMETER_COUNT = 21

        // FSRS-6 defaults from the official algorithm description and py-fsrs
        // 6.3.2. Replaced later by locally optimised parameters computed from
        // the append-only `reviews` log; no review has to leave the phone for it.
        val DEFAULT_W = listOf(
            0.2120, 1.2931, 2.3065, 8.2956,
            6.4133, 0.8334, 3.0194, 0.0010,
            1.8722, 0.1666, 0.7960, 1.4835,
            0.0614, 0.2629, 1.6483, 0.6014,
            1.8729, 0.5425, 0.0912, 0.0658,
            0.1542
        )
    }
}

/** FSRS-6. Item level only; the component layer never feeds back into it. */
object Fsrs {

    private const val MIN_STABILITY = 0.1
    private const val MAX_STABILITY = 36500.0

    private fun decay(p: FsrsParams): Double = -p.w[20]

    private fun factor(p: FsrsParams): Double = 0.9.pow(1.0 / decay(p)) - 1.0

    fun retrievability(
        elapsedDays: Double,
        stability: Double,
        p: FsrsParams = FsrsParams()
    ): Double =
        (1.0 + factor(p) * elapsedDays / max(stability, MIN_STABILITY)).pow(decay(p))

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
    fun intervalDays(
        stability: Double,
        desiredRetention: Double,
        p: FsrsParams = FsrsParams()
    ): Double = max(
        1.0,
        stability / factor(p) * (desiredRetention.pow(1.0 / decay(p)) - 1.0)
    )

    fun initialStability(rating: Rating, p: FsrsParams): Double =
        clampStability(p.w[rating.value - 1])

    fun initialDifficulty(rating: Rating, p: FsrsParams): Double =
        clampDifficulty(initialDifficultyRaw(rating, p))

    fun initial(rating: Rating, p: FsrsParams): MemoryState =
        MemoryState(initialStability(rating, p), initialDifficulty(rating, p))

    fun next(
        state: MemoryState,
        elapsedDays: Double,
        rating: Rating,
        p: FsrsParams
    ): MemoryState {
        val s = if (elapsedDays < 1.0) {
            shortTermStability(state.stability, rating, p)
        } else {
            val r = retrievability(elapsedDays, state.stability, p)
            if (rating == Rating.AGAIN) {
                forgetStability(state.difficulty, state.stability, r, p)
            } else {
                recallStability(state.difficulty, state.stability, r, rating, p)
            }
        }
        // The official scheduler updates stability from the difficulty that
        // existed before this answer, then computes the next difficulty. Using
        // D' in the stability formula is a plausible-looking but different
        // model, and diverges most on HARD and EASY.
        val d = nextDifficulty(state.difficulty, rating, p)
        return MemoryState(clampStability(s), d)
    }

    private fun initialDifficultyRaw(rating: Rating, p: FsrsParams): Double =
        p.w[4] - exp(p.w[5] * (rating.value - 1)) + 1.0

    private fun nextDifficulty(d: Double, rating: Rating, p: FsrsParams): Double {
        // FSRS-5/6 damp the change as difficulty approaches 10, then pull it
        // very slightly towards the unclamped initial EASY difficulty. Earlier
        // versions used D0(GOOD) and had no damping.
        val delta = -p.w[6] * (rating.value - 3)
        val damped = d + (10.0 - d) * delta / 9.0
        val reverted = p.w[7] * initialDifficultyRaw(Rating.EASY, p) +
            (1.0 - p.w[7]) * damped
        return clampDifficulty(reverted)
    }

    /**
     * FSRS-6's short-term memory model, used for every repeat under 24 hours.
     *
     * A failed card is deliberately put back into Ikna's current session. That
     * makes this a common path here, not an Anki compatibility detail. HARD,
     * GOOD and EASY may not weaken a memory; AGAIN is allowed to do so.
     */
    private fun shortTermStability(s: Double, rating: Rating, p: FsrsParams): Double {
        var increase = exp(p.w[17] * (rating.value - 3 + p.w[18])) * s.pow(-p.w[19])
        if (rating != Rating.AGAIN) increase = max(increase, 1.0)
        return clampStability(s * increase)
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
        val longTerm = p.w[11] *
            d.pow(-p.w[12]) *
            ((s + 1.0).pow(p.w[13]) - 1.0) *
            exp((1.0 - r) * p.w[14])
        val shortTermLimit = s / exp(p.w[17] * p.w[18])
        return min(longTerm, shortTermLimit)
    }

    private fun clampStability(s: Double) = min(max(s, MIN_STABILITY), MAX_STABILITY)
    private fun clampDifficulty(d: Double) = min(max(d, 1.0), 10.0)
}
