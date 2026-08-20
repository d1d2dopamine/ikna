package dev.ikna.domain.fsrs

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One answer, as the optimiser needs it: which card, when, and what was pressed.
 *
 * [cardKey] is `CardEntity.key` -- "chunkId:level". Levels are separate cards
 * with separate memories, so they are separate histories here too.
 */
data class ReviewSample(
    val cardKey: String,
    val ts: Long,
    val rating: Rating
)

/**
 * The outcome of one fitting attempt, including the numbers that justify it.
 *
 * [params] is null unless the fit earned its place: see [Verdict].
 */
data class Optimisation(
    val params: FsrsParams?,
    val scoredAnswers: Int,
    val heldOutLossDefaults: Double,
    val heldOutLossOptimised: Double,
    val verdict: Verdict
) {
    /** How much better the fit predicted the weeks it never trained on. */
    val improvement: Double
        get() = heldOutLossDefaults - heldOutLossOptimised
}

enum class Verdict {
    /** Not enough answers spaced a day or more apart to conclude anything. */
    TOO_FEW_ANSWERS,

    /** A fit was computed and did not beat the defaults on unseen weeks. */
    NO_IMPROVEMENT,

    /** The fit predicted unseen weeks better and may replace the defaults. */
    ACCEPTED
}

/**
 * Fits the twenty-one FSRS-6 weights to one person's own review log, on their
 * own phone.
 *
 * `FsrsParams.DEFAULT_W` are population averages: fitted across many
 * collections, by many people, learning many things. They are a good prior and
 * they are nobody's memory in particular. The append-only `reviews` log already
 * holds everything needed to do better -- a timestamp and a grade per answer --
 * and this app already promises that the log never leaves the device, so the
 * fitting happens here and that promise stays intact.
 *
 * Five decisions, each guarding against a specific way this could be worse than
 * not having it at all:
 *
 *  1. **The model being fitted is the shipping model.** Every prediction goes
 *     through [Fsrs]. A private copy of the formulas would drift from the
 *     scheduler and the drift would be invisible, because both halves would go
 *     on producing plausible numbers.
 *  2. **Binary log loss on "was this recalled"**, against the retrievability the
 *     model predicted for the real elapsed time. Answers less than
 *     [MIN_ELAPSED_DAYS] after the previous one are replayed but not scored:
 *     retrievability there is ~1 by construction, so scoring them would measure
 *     how often a session repeats a failed card, not how memory behaves.
 *  3. **A pull towards the defaults** ([REGULARISATION]). With a few hundred
 *     answers the loss surface is nearly flat in several directions, and an
 *     unconstrained fit walks to the edge of the allowed range for whichever
 *     weight the noise happened to favour. This was not a theory: it is what the
 *     prototype did.
 *  4. **A held-out gate.** The most recent [HELD_OUT_SHARE] of the timeline is
 *     never trained on, and the fit is used only if it predicts that stretch
 *     better than the defaults do. This is the part that makes the feature safe.
 *  5. **Determinism.** Fixed-step coordinate descent, no randomness: the same
 *     log gives the same weights. A result that cannot be reproduced from an
 *     exported log cannot be argued with either.
 *
 * What this deliberately does not do: estimate `desiredRetention`. How much
 * daily work is acceptable is not a question a log can answer, and the load
 * governor already owns it.
 *
 * See `docs/FSRS-OPTIMIZER.md` for the measured behaviour, including the
 * uncomfortable part -- fitting recovers prediction, not parameters.
 */
object FsrsOptimizer {

    /**
     * Below this many scored answers the estimator refuses.
     *
     * Chosen from the prototype rather than from taste: on a synthetic log of
     * about 640 scored answers the fit was worse on held-out weeks at every
     * regularisation strength tried, and the gate rejected it. Fitting far below
     * this is not personalisation, it is fitting to noise and calling it
     * personalisation.
     */
    const val MIN_SCORED_ANSWERS = 500

    /** Same-session repeats are replayed but not scored. */
    const val MIN_ELAPSED_DAYS = 1.0

    /** The most recent share of the timeline, never trained on. */
    const val HELD_OUT_SHARE = 0.3

    /** Held-out loss has to improve by at least this much, in nats. */
    const val MIN_IMPROVEMENT = 0.001

    /** Strength of the pull towards the published defaults. */
    const val REGULARISATION = 0.1

    private const val MAX_PASSES = 6
    private const val MAX_ANSWERS = 20_000
    private const val EPS = 1e-6
    private const val FIRST_STEP = 0.08

    /**
     * The range each weight is allowed to take, in the order of
     * `FsrsParams.DEFAULT_W`.
     *
     * These are the bounds the reference optimiser uses. They are not
     * decoration: several of them are what keeps a formula defined at all (a
     * stability of zero, a difficulty outside 1..10), and the rest stop a fit
     * from wandering somewhere no memory could be.
     */
    val BOUNDS: List<ClosedFloatingPointRange<Double>> = listOf(
        0.01..100.0,   // w0..w3: initial stability per grade, in days
        0.01..100.0,
        0.01..100.0,
        0.01..100.0,
        1.0..10.0,     // w4: initial difficulty
        0.001..4.0,    // w5: how fast initial difficulty falls with the grade
        0.001..4.0,    // w6: difficulty change per grade
        0.001..0.75,   // w7: pull back towards the initial EASY difficulty
        0.0..4.5,      // w8: stability growth on a recall
        0.0..0.8,      // w9: diminishing returns as stability rises
        0.001..3.5,    // w10: how much a hard-won recall is worth
        0.001..5.0,    // w11: post-lapse stability
        0.001..0.25,   // w12: post-lapse effect of difficulty
        0.001..0.9,    // w13: post-lapse effect of previous stability
        0.0..4.0,      // w14: post-lapse effect of retrievability
        0.0..1.0,      // w15: HARD penalty
        1.0..6.0,      // w16: EASY bonus
        0.0..2.0,      // w17: short-term step size
        0.0..2.0,      // w18: short-term offset
        0.0..0.8,      // w19: short-term decay with stability
        0.1..0.8       // w20: the forgetting curve's shape
    )

    init {
        require(BOUNDS.size == FsrsParams.PARAMETER_COUNT) {
            "There must be exactly one range per FSRS parameter: " +
                "${BOUNDS.size} ranges for ${FsrsParams.PARAMETER_COUNT} weights."
        }
    }

    /**
     * Fits weights to [samples] and says whether they are worth using.
     *
     * Pure and deterministic: no clock, no storage, no I/O. It is a background
     * job's worth of arithmetic -- seconds, not milliseconds -- so it must never
     * be called on the path of answering a card.
     *
     * @param desiredRetention carried through unchanged into the result. It is a
     *   decision, not an estimate.
     */
    fun optimise(
        samples: List<ReviewSample>,
        desiredRetention: Double = FsrsParams().desiredRetention
    ): Optimisation {
        val histories = histories(samples)
        val scored = scoredTimestamps(histories)

        if (scored.size < MIN_SCORED_ANSWERS) {
            return Optimisation(
                params = null,
                scoredAnswers = scored.size,
                heldOutLossDefaults = Double.NaN,
                heldOutLossOptimised = Double.NaN,
                verdict = Verdict.TOO_FEW_ANSWERS
            )
        }

        val cut = heldOutCut(scored)
        val defaults = FsrsParams(desiredRetention = desiredRetention)
        val fitted = descend(histories, cut, desiredRetention)

        val lossDefaults = logLoss(defaults, histories, cut, null).mean
        val lossFitted = logLoss(fitted, histories, cut, null).mean
        val better = lossFitted < lossDefaults - MIN_IMPROVEMENT

        return Optimisation(
            params = if (better) fitted else null,
            scoredAnswers = scored.size,
            heldOutLossDefaults = lossDefaults,
            heldOutLossOptimised = lossFitted,
            verdict = if (better) Verdict.ACCEPTED else Verdict.NO_IMPROVEMENT
        )
    }

    /**
     * Groups answers into one ordered history per card, newest
     * [MAX_ANSWERS] first kept.
     *
     * The cap is there so that a decade-long log cannot turn a background job
     * into a battery complaint; it keeps the most recent answers, which are also
     * the ones that describe the memory the user has now.
     */
    internal fun histories(samples: List<ReviewSample>): List<List<ReviewSample>> {
        val byTime = samples.sortedBy { it.ts }
        val recent = if (byTime.size > MAX_ANSWERS) {
            byTime.subList(byTime.size - MAX_ANSWERS, byTime.size)
        } else {
            byTime
        }
        return recent
            .sortedWith(compareBy({ it.cardKey }, { it.ts }))
            .groupBy { it.cardKey }
            .values
            .toList()
    }

    /**
     * Mean binary log loss of [params] over the answers in the window, plus how
     * many were scored.
     *
     * Every answer is replayed so that the memory state stays right; [from] and
     * [until] decide only which of them count towards the score. That is what
     * makes the held-out split honest: the last weeks are predicted from a state
     * built out of the earlier ones, exactly as they were lived.
     *
     * @param from inclusive lower bound on the answer's timestamp, or null.
     * @param until exclusive upper bound on the answer's timestamp, or null.
     */
    internal fun logLoss(
        params: FsrsParams,
        histories: List<List<ReviewSample>>,
        from: Long?,
        until: Long?
    ): Loss {
        var total = 0.0
        var count = 0

        for (history in histories) {
            var state: MemoryState? = null
            var last = 0L
            for (sample in history) {
                val previous = state
                if (previous == null) {
                    // The first answer establishes the memory; there is nothing
                    // to have predicted about it.
                    state = Fsrs.initial(sample.rating, params)
                    last = sample.ts
                    continue
                }

                val elapsedDays =
                    (sample.ts - last).coerceAtLeast(0L).toDouble() / DAY_MS
                val inWindow = (from == null || sample.ts >= from) &&
                    (until == null || sample.ts < until)

                if (elapsedDays >= MIN_ELAPSED_DAYS && inWindow) {
                    val predicted =
                        Fsrs.retrievability(elapsedDays, previous.stability, params)
                            .coerceIn(EPS, 1.0 - EPS)
                    total += if (sample.rating == Rating.AGAIN) {
                        -ln(1.0 - predicted)
                    } else {
                        -ln(predicted)
                    }
                    count++
                }

                state = Fsrs.next(previous, elapsedDays, sample.rating, params)
                last = sample.ts
            }
        }

        return Loss(if (count == 0) Double.NaN else total / count, count)
    }

    internal data class Loss(val mean: Double, val count: Int)

    /** Timestamps of the answers that can be scored at all, in order. */
    private fun scoredTimestamps(histories: List<List<ReviewSample>>): List<Long> {
        val out = ArrayList<Long>()
        for (history in histories) {
            var last: Long? = null
            for (sample in history) {
                val previous = last
                if (previous != null) {
                    val elapsedDays =
                        (sample.ts - previous).coerceAtLeast(0L).toDouble() / DAY_MS
                    if (elapsedDays >= MIN_ELAPSED_DAYS) out.add(sample.ts)
                }
                last = sample.ts
            }
        }
        out.sort()
        return out
    }

    /**
     * The timestamp where the held-out stretch begins.
     *
     * Split by answer count rather than by calendar, so that a fortnight away
     * from the app does not silently become the entire test set.
     */
    private fun heldOutCut(scored: List<Long>): Long {
        val index = ((1.0 - HELD_OUT_SHARE) * scored.size).toInt()
            .coerceIn(1, scored.size - 1)
        return scored[index]
    }

    /**
     * Coordinate descent from the defaults: try each weight up and down, keep
     * what helps, halve the step, repeat.
     *
     * Not gradient descent, on purpose. The gradient of this loss through the
     * replay is not available in closed form, numerical gradients cost the same
     * as these probes, and this version has no learning rate to tune and no way
     * to diverge. It is also short enough to read, which for a number that
     * decides someone's schedule is worth more than elegance.
     */
    private fun descend(
        histories: List<List<ReviewSample>>,
        cut: Long,
        desiredRetention: Double
    ): FsrsParams {
        val w = FsrsParams.DEFAULT_W.toMutableList()
        var best = objective(w, histories, cut, desiredRetention)
        var step = FIRST_STEP

        repeat(MAX_PASSES) {
            var moved = false
            for (index in w.indices) {
                val range = BOUNDS[index]
                val span = range.endInclusive - range.start
                for (scale in listOf(step, -step, step * 0.25, -step * 0.25)) {
                    val current = w[index]
                    val candidate = clamp(current + scale * span, range)
                    if (candidate == current) continue
                    w[index] = candidate
                    val score = objective(w, histories, cut, desiredRetention)
                    if (score < best) {
                        best = score
                        moved = true
                    } else {
                        w[index] = current
                    }
                }
            }
            if (!moved) return FsrsParams(w.toList(), desiredRetention)
            step *= 0.5
        }
        return FsrsParams(w.toList(), desiredRetention)
    }

    /** Training loss plus the pull towards the defaults. */
    private fun objective(
        w: List<Double>,
        histories: List<List<ReviewSample>>,
        cut: Long,
        desiredRetention: Double
    ): Double {
        val loss = logLoss(FsrsParams(w.toList(), desiredRetention), histories, null, cut)
        return if (loss.count == 0) Double.MAX_VALUE else loss.mean + penalty(w)
    }

    /**
     * Squared distance from the defaults, measured as a fraction of each
     * weight's own range so that days and exponents are comparable.
     */
    private fun penalty(w: List<Double>): Double {
        var sum = 0.0
        for (index in w.indices) {
            val range = BOUNDS[index]
            val span = range.endInclusive - range.start
            val delta = (w[index] - FsrsParams.DEFAULT_W[index]) / span
            sum += delta * delta
        }
        return REGULARISATION * sum / w.size
    }

    private fun clamp(value: Double, range: ClosedFloatingPointRange<Double>): Double =
        min(max(value, range.start), range.endInclusive)
}
