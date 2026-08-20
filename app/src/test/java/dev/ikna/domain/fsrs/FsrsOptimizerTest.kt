package dev.ikna.domain.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * What these tests can and cannot check.
 *
 * The logs here are simulated with the shipping model itself, so the true
 * parameters are known. That makes it possible to check the machinery -- the
 * loss orders models correctly, the refusal happens, the search stays inside its
 * bounds, the same input gives the same output -- and it deliberately does not
 * check that a fit is accepted. Whether the held-out gate opens depends on how
 * much log there is and how lucky the noise was, and a test that demanded
 * acceptance would be asserting the luck.
 *
 * See docs/FSRS-OPTIMIZER.md for the numbers behind that choice.
 */
class FsrsOptimizerTest {

    @Test
    fun `a short log is refused rather than fitted`() {
        val result = FsrsOptimizer.optimise(log(cards = 6, seed = 1))

        assertEquals(Verdict.TOO_FEW_ANSWERS, result.verdict)
        assertNull(
            "Refusing has to mean no parameters at all. Returning a fit next to a " +
                "warning invites a caller to use it anyway.",
            result.params
        )
        assertTrue(
            "A refusal should still report how much log there was, so the caller " +
                "can say how far off the threshold the user is.",
            result.scoredAnswers in 1 until FsrsOptimizer.MIN_SCORED_ANSWERS
        )
    }

    @Test
    fun `there is one bound per parameter and every default is inside it`() {
        assertEquals(
            "A missing bound would leave one weight free to be fitted to anything.",
            FsrsParams.PARAMETER_COUNT,
            FsrsOptimizer.BOUNDS.size
        )
        FsrsParams.DEFAULT_W.forEachIndexed { index, value ->
            assertTrue(
                "The published default w$index = $value is outside the range this " +
                    "file allows, so the search would start by moving it. One of " +
                    "the two is wrong.",
                value in FsrsOptimizer.BOUNDS[index]
            )
        }
    }

    @Test
    fun `the same log gives the same answer twice`() {
        val samples = log(cards = 220, seed = 7)

        val first = FsrsOptimizer.optimise(samples)
        val second = FsrsOptimizer.optimise(samples)

        assertEquals(first.verdict, second.verdict)
        assertEquals(
            "A schedule the user cannot reproduce from their own exported log is " +
                "a schedule they cannot argue with.",
            first.params?.w,
            second.params?.w
        )
        assertEquals(first.heldOutLossOptimised, second.heldOutLossOptimised, 0.0)
    }

    @Test
    fun `a fit is either inside its bounds and better, or not returned at all`() {
        val result = FsrsOptimizer.optimise(log(cards = 220, seed = 3))
        val params = result.params

        if (params == null) {
            // A legitimate outcome, and the reason this test is written as an
            // either-or: rejection on a log this size is what the prototype saw
            // as often as not.
            assertEquals(Verdict.NO_IMPROVEMENT, result.verdict)
            return
        }

        assertEquals(Verdict.ACCEPTED, result.verdict)
        assertEquals(FsrsParams.PARAMETER_COUNT, params.w.size)
        params.w.forEachIndexed { index, value ->
            assertTrue(
                "Fitted w$index = $value left the range the model is defined on.",
                value in FsrsOptimizer.BOUNDS[index]
            )
        }
        assertTrue(
            "An accepted fit has to have beaten the defaults on the weeks it was " +
                "never trained on, by more than the threshold.",
            result.heldOutLossOptimised <
                result.heldOutLossDefaults - FsrsOptimizer.MIN_IMPROVEMENT
        )
        assertTrue(result.improvement > FsrsOptimizer.MIN_IMPROVEMENT)
    }

    @Test
    fun `the loss prefers the model the log was generated with`() {
        val histories = FsrsOptimizer.histories(log(cards = 140, seed = 11))
        val truth = FsrsParams()

        // A negative control has to be wrong in a way the measurement can see.
        // Changing only the forgetting curve is not: every decay is calibrated so
        // that retrievability is exactly the desired retention at the scheduled
        // interval, and the simulator reviews at that interval, so the two curves
        // agree at precisely the points where they are compared. Shrinking the
        // initial stabilities and flattening the growth on a recall makes the
        // model expect forgetting that does not happen, which does show up.
        val wrong = FsrsParams(
            FsrsParams.DEFAULT_W.mapIndexed { index, value ->
                when {
                    index <= 3 -> value * 0.1
                    index == 8 -> 0.0
                    else -> value
                }
            }
        )

        val truthLoss = FsrsOptimizer.logLoss(truth, histories, null, null)
        val wrongLoss = FsrsOptimizer.logLoss(wrong, histories, null, null)

        assertEquals(
            "Both models have to be scored on the same answers, or the comparison " +
                "is between two different questions.",
            truthLoss.count,
            wrongLoss.count
        )
        assertTrue(
            "The loss is the only judge of a fit. If it cannot tell the true model " +
                "from a badly wrong one, nothing else in this file means anything.",
            truthLoss.mean < wrongLoss.mean
        )
    }

    @Test
    fun `answers less than a day apart are replayed but not scored`() {
        val key = "chunk-0:0"
        val history = listOf(
            ReviewSample(key, START, Rating.GOOD),
            ReviewSample(key, START + 2 * 3_600_000L, Rating.AGAIN),
            ReviewSample(key, START + 3 * DAY_MS, Rating.GOOD)
        )

        val loss = FsrsOptimizer.logLoss(
            FsrsParams(),
            FsrsOptimizer.histories(history),
            null,
            null
        )

        assertEquals(
            "Only the answer three days later can be scored. Scoring the repeat two " +
                "hours in would measure how often a session shows a failed card " +
                "again, not how memory behaves.",
            1,
            loss.count
        )
    }

    // -----------------------------------------------------------------------
    // A simulated review log, generated with the shipping model.
    // -----------------------------------------------------------------------

    /**
     * Reviews [cards] cards over [days] days, at the intervals the scheduler
     * would have chosen, failing them at the rate the model predicts.
     */
    private fun log(cards: Int, seed: Long, days: Int = 420): List<ReviewSample> {
        val random = Random(seed)
        val params = FsrsParams()
        val samples = ArrayList<ReviewSample>()

        for (card in 0 until cards) {
            val key = "chunk-$card:0"

            // Introductions are spread out rather than all on day one, because a
            // governor introduces a few new chunks a day and that is what makes
            // the newest cards in the log young.
            var day = random.nextDouble() * days / 3.0
            val first = grade(random)
            samples.add(ReviewSample(key, at(day), first))
            var state = Fsrs.initial(first, params)

            while (true) {
                val interval =
                    Fsrs.intervalDays(state.stability, params.desiredRetention, params)
                day += interval
                if (day > days) break

                val recalled = random.nextDouble() <
                    Fsrs.retrievability(interval, state.stability, params)
                val rating = if (recalled) grade(random) else Rating.AGAIN

                samples.add(ReviewSample(key, at(day), rating))
                state = Fsrs.next(state, interval, rating, params)
            }
        }
        return samples
    }

    /** A recalled answer: mostly GOOD, sometimes HARD or EASY. */
    private fun grade(random: Random): Rating {
        val roll = random.nextDouble()
        return when {
            roll < 0.1 -> Rating.HARD
            roll > 0.9 -> Rating.EASY
            else -> Rating.GOOD
        }
    }

    private fun at(day: Double): Long = START + (day * DAY_MS).toLong()

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
