package dev.ikna.domain.grading

import dev.ikna.data.db.CardEntity
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.applyRecordedReview
import dev.ikna.data.repo.gradingWindowFromReviews
import dev.ikna.data.repo.outcomeRating
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.session.ReviewSignals
import org.junit.Assert.*
import org.junit.Test

class DerivedGradingTest {
    private val start = 1_700_000_000_000L
    private fun window(size: Int = 80, level: Int = 0) = TimingWindow().also { w ->
        repeat(size) { w.add(TimingSample(1_000L + it * 100L, level, 25)) }
    }
    private fun signals(ms: Long? = 4_000L, peek: Boolean? = false, semantics: String? = PEEK_OPTIONAL) = ReviewSignals(
        latencyMs = ms, swipeVelocityX = 950f, peeked = peek,
        inputMethod = INPUT_SWIPE, peekSemantics = semantics
    )
    private fun decide(ms: Long? = 4_000, peek: Boolean? = false, input: Rating = Rating.GOOD,
                       enabled: Boolean = true, w: TimingWindow = window(), semantics: String? = PEEK_OPTIONAL) =
        DerivedGrading.decide(input, signals(ms, peek, semantics), 0, 25, w, enabled)

    @Test fun `experiment defaults off`() { assertFalse(IknaSettings().derivedGrading) }

    @Test fun `disabled is binary regardless of clear signals`() {
        for (ms in listOf(500L, 4_000L, 10_000L)) {
            assertEquals(Rating.GOOD, decide(ms, peek = true, enabled = false).rating)
        }
    }

    @Test fun `fifty prior observations are required and current answer cannot train itself`() {
        val w = window(49)
        assertEquals(Rating.GOOD, decide(500L, w = w).rating)
        assertEquals(49, w.size)
        w.add(TimingSample(3_000L, 0, 25))
        assertEquals(Rating.EASY, decide(500L, w = w).rating)
    }

    @Test fun `level requires its own population instead of multiplying another level`() {
        val w = window(70, level = 1)
        assertEquals(Rating.GOOD, decide(500L, w = w).rating)
        repeat(19) { w.add(TimingSample(1_000L + it * 100L, 0, 25)) }
        assertEquals(Rating.GOOD, decide(500L, w = w).rating)
        w.add(TimingSample(4_000L, 0, 25))
        assertEquals(Rating.EASY, decide(500L, w = w).rating)
    }

    @Test fun `ring evicts oldest and never grows past two hundred`() {
        val w = TimingWindow()
        repeat(250) { w.add(TimingSample(1_000L + it, it % 3, 25)) }
        assertEquals(200, w.size)
        assertEquals(1_050L, w.samples().first().latencyMs)
        assertEquals(1_249L, w.samples().last().latencyMs)
    }

    @Test fun `four mappings and ties are conservative`() {
        assertEquals(Rating.AGAIN, decide(500L, input = Rating.AGAIN).rating)
        assertEquals(Rating.HARD, decide(500L, peek = true).rating)
        assertEquals(Rating.HARD, decide(10_000L).rating)
        assertEquals(Rating.GOOD, decide(4_000L).rating)
        assertEquals(Rating.EASY, decide(500L).rating)
        // Window 1000..8900 has exact quartile ties 2975 / 6925.
        assertEquals(Rating.GOOD, decide(2_975L).rating)
        assertEquals(Rating.GOOD, decide(6_925L).rating)
    }

    @Test fun `manual hard and easy are never reinterpreted`() {
        assertEquals(Rating.HARD, decide(input = Rating.HARD).rating)
        assertEquals(Rating.EASY, decide(input = Rating.EASY).rating)
    }

    @Test fun `unknown timing and focus loss fall back even with a peek`() {
        assertEquals(Rating.GOOD, decide(ms = null, peek = true).rating)
        val invalid = signals(500L, true).copy(timingDiscardReason = "focus_lost")
        val decision = DerivedGrading.decide(Rating.GOOD, invalid, 0, 25, window(), true)
        assertEquals(Rating.GOOD, decision.rating)
        assertNull(decision.acceptedSample)
        assertEquals("focus_lost", decision.timingDiscardReason)
    }

    @Test fun `unknown peek cannot lengthen an interval`() {
        assertEquals(Rating.GOOD, decide(500L, peek = null).rating)
        assertEquals(Rating.GOOD, decide(500L, semantics = null).rating)
    }

    @Test fun `required reveal is not all hard and cannot manufacture easy`() {
        assertEquals(Rating.GOOD, decide(4_000L, true, semantics = PEEK_REQUIRED).rating)
        assertEquals(Rating.GOOD, decide(500L, true, semantics = PEEK_REQUIRED).rating)
        assertEquals(Rating.HARD, decide(10_000L, true, semantics = PEEK_REQUIRED).rating)
    }

    @Test fun `flat timing window is not evidence`() {
        val w = TimingWindow()
        repeat(80) { w.add(TimingSample(2_000L, 0, 25)) }
        assertEquals(Rating.GOOD, decide(500L, w = w).rating)
        assertEquals(Rating.GOOD, decide(4_000L, w = w).rating)
    }

    @Test fun `hard ceiling and personal median reject only the stopwatch`() {
        val long = decide(60_001L)
        assertEquals(Rating.GOOD, long.rating)
        assertNull(long.acceptedSample)
        val distracted = decide(40_000L)
        assertEquals("above_personal_ceiling", distracted.timingDiscardReason)
        assertNull(distracted.acceptedSample)
        assertEquals(Rating.GOOD, distracted.rating)
        assertEquals(Rating.AGAIN, decide(40_000L, input = Rating.AGAIN).rating)
    }

    @Test fun `latency is normalized by length with level stratification`() {
        val w = window()
        val short = DerivedGrading.decide(Rating.GOOD, signals(4_000L), 0, 25, w, true)
        val long = DerivedGrading.decide(Rating.GOOD, signals(8_000L), 0, 100, w, true)
        assertEquals(short.rating, long.rating)
        assertEquals(short.acceptedSample!!.normalized, long.acceptedSample!!.normalized, 0.0)
    }

    @Test fun `noise cannot turn good into easy`() {
        val grades = listOf(500L, 4_000L, 10_000L, 40_000L).map { decide(it).rating }
        assertEquals(listOf(Rating.EASY, Rating.GOOD, Rating.HARD, Rating.GOOD), grades)
    }

    @Test fun `keyboard accessibility invalid lengths and nonfinite velocities are never calibrated`() {
        for (method in listOf(INPUT_KEYBOARD, INPUT_ACCESSIBILITY, null)) {
            val result = DerivedGrading.decide(Rating.GOOD, signals().copy(inputMethod = method), 0, 25, window(), true)
            assertNull(result.acceptedSample)
            assertEquals(Rating.GOOD, result.rating)
        }
        for (length in listOf(null, 0, -1, 4_001)) {
            assertNull(DerivedGrading.decide(Rating.GOOD, signals(), 0, length, window(), true).acceptedSample)
        }
        for (velocity in listOf(null, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertNull(DerivedGrading.decide(Rating.GOOD, signals().copy(swipeVelocityX = velocity), 0, 25, window(), true).acceptedSample)
        }
        assertNull(decide(0L).acceptedSample)
    }

    private fun card(stability: Double = 10.0, fresh: Boolean = false) = CardEntity(
        chunkId = "one", level = 0, stability = stability, difficulty = 5.0,
        dueAt = start, lastReviewAt = if (fresh) null else start - 3 * DAY_MS,
        introducedAt = start - 10 * DAY_MS, reps = if (fresh) 0 else 4,
        isNew = fresh
    )

    @Test fun `actual due times and stability stay within thirty percent including boundaries`() {
        for (hour in listOf<Int?>(null, 0, 4)) {
            val scheduler = Scheduler(dayStartHour = hour)
            for (s in listOf(0.1, 1.0, 10.0, 200.0, 20_000.0)) {
                for (fresh in listOf(false, true)) {
                    for (elapsed in listOf(0L, 60_000L, DAY_MS / 2, 3 * DAY_MS, 90 * DAY_MS)) {
                        val before = card(s, fresh).copy(lastReviewAt = if (fresh) null else start - elapsed)
                        val good = scheduler.apply(before, Rating.GOOD, start)
                        for (rating in listOf(Rating.HARD, Rating.EASY)) {
                            val result = scheduler.applyDerivedV1(before, rating, start)
                            val delta = result.card.dueAt - start
                            val baseline = good.card.dueAt - start
                            assertTrue(delta >= baseline * 0.7 - 1.0)
                            assertTrue(delta <= baseline * 1.3 + 1.0)
                            assertTrue(result.after.stability >= good.after.stability * 0.7 - 1e-9)
                            assertTrue(result.after.stability <= good.after.stability * 1.3 + 1e-9)
                            assertEquals(result.after.stability, result.card.stability, 0.0)
                            assertEquals(before.lapses, result.card.lapses)
                            if (rating == Rating.EASY) assertTrue(delta >= baseline)
                            else assertTrue(delta <= baseline)
                        }
                    }
                }
            }
        }
    }

    @Test fun `live versioned decisions survive JSONL replay exactly with flag switches`() {
        val scheduler = Scheduler(dayStartHour = 4)
        val w = TimingWindow()
        var live = card(fresh = true)
        var replay = live
        repeat(160) { i ->
            val now = start + i * DAY_MS
            val input = if (i % 17 == 0) Rating.AGAIN else Rating.GOOD
            val ms = when (i % 4) { 0 -> 500L; 1 -> 3_000L; 2 -> 7_000L; else -> 12_000L }
            val raw = signals(ms, peek = i % 11 == 0)
            val decision = DerivedGrading.decide(input, raw, 0, 25, w, enabled = i < 140)
            val version = if (decision.rating != input) 1 else null
            val result = if (version == 1) scheduler.applyDerivedV1(live, decision.rating, now)
                         else scheduler.apply(live, input, now)
            val record = ReviewRecord(
                id = i + 1L, chunkId = "one", ts = now, rating = decision.rating.value,
                inputRating = input.value, gradingVersion = version, gradingReason = decision.reason,
                presentationLength = 25, inputMethod = INPUT_SWIPE, peekSemantics = PEEK_OPTIONAL,
                latencyMs = ms, swipeVelocityX = 950f, peeked = raw.peeked,
                timingDiscardReason = decision.timingDiscardReason,
                prevStability = live.stability, prevDifficulty = live.difficulty,
                prevDueAt = live.dueAt, prevLastReviewAt = live.lastReviewAt,
                prevReps = live.reps, prevLapses = live.lapses, prevIsNew = live.isNew,
                stabilityBefore = result.before.stability, stabilityAfter = result.after.stability
            )
            val json = ReviewRecord.json.encodeToString(ReviewRecord.serializer(), record)
            val restored = ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), json)
            assertEquals(record, restored)
            replay = scheduler.applyRecordedReview(replay, restored.toEntity()).card
            live = result.card
            assertEquals("answer $i must replay without today's settings or calibration", live, replay)
            decision.acceptedSample?.let(w::add)
        }
    }

    @Test fun `legacy and explicit grades replay as before`() {
        val scheduler = Scheduler()
        for (rating in Rating.entries) {
            val row = ReviewRecord(chunkId = "one", ts = start, rating = rating.value).toEntity()
            assertEquals(scheduler.apply(card(), rating, start), scheduler.applyRecordedReview(card(), row))
        }
    }

    @Test fun `derived hard is success for governor but manual hard keeps its old meaning`() {
        val manual = ReviewRecord(chunkId = "one", ts = start, rating = 2).toEntity()
        val derived = manual.copy(inputRating = 3, gradingVersion = 1)
        assertEquals(2, manual.outcomeRating)
        assertEquals(3, derived.outcomeRating)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown schedule versions cannot silently replay differently`() {
        Scheduler().applyRecordedReview(card(), ReviewRecord(chunkId = "one", ts = start, rating = 2,
            inputRating = 3, gradingVersion = 99).toEntity())
    }

    @Test fun `persisted ring restores in order and ignores missing and manual samples`() {
        val rows = (0..249).map { i -> ReviewRecord(chunkId = "one", ts = start + i, rating = 3,
            inputRating = 3, latencyMs = i + 1_000L, presentationLength = 25,
            inputMethod = INPUT_SWIPE, swipeVelocityX = 0f).toEntity() }
        val w = gradingWindowFromReviews(rows + rows.last().copy(inputMethod = INPUT_KEYBOARD))
        assertEquals(200, w.size)
        assertEquals(1_050L, w.samples().first().latencyMs)
        assertEquals(1_249L, w.samples().last().latencyMs)
    }

    private fun fixture() = (0 until 240).map { i -> ReviewRecord(
        id = i + 1L, chunkId = "card-${i % 12}", ts = start + i * DAY_MS,
        rating = if (i % 7 == 0) 1 else 3, inputRating = if (i % 7 == 0) 1 else 3,
        latencyMs = 500L + (i % 21) * 500L, presentationLength = 25,
        inputMethod = INPUT_SWIPE, peekSemantics = PEEK_OPTIONAL, peeked = i % 13 == 0,
        swipeVelocityX = 950f, synthetic = true
    ) }

    @Test fun `synthetic evaluation is finite paired and cannot authorize rollout`() {
        val records = fixture()
        val result = GradingEvaluator.evaluate(records)
        assertEquals("synthetic", result.sourceKind)
        assertEquals(240, result.activeAnswers)
        assertEquals(228, result.scoredAnswers)
        assertEquals(60, result.heldOutAnswers)
        assertTrue(result.binaryBrier!! in 0.0..1.0)
        assertTrue(result.derivedBrier!! in 0.0..1.0)
        assertTrue(result.binaryLogLoss!!.isFinite())
        assertTrue(result.derivedHard > 0)
        assertTrue(result.derivedEasy > 0)
        assertFalse(result.warrantsRealWorldReview)
        assertEquals(result, GradingEvaluator.evaluate(records + records.take(3)))
    }

    @Test fun `undo rows are not predictions or calibration samples`() {
        val records = fixture()
        val undo = records.first().copy(id = 1_000L, ts = start + 500 * DAY_MS, rating = 0,
            inputRating = null, undoOf = 1L)
        val result = GradingEvaluator.evaluate(records + undo)
        assertEquals(239, result.activeAnswers)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mixed real and synthetic data are rejected`() {
        GradingEvaluator.evaluate(fixture() + fixture().first().copy(synthetic = false))
    }

    @Test fun `empty log reports no evidence`() {
        val result = GradingEvaluator.evaluate(emptyList())
        assertNull(result.binaryBrier)
        assertNull(result.derivedBrier)
        assertFalse(result.warrantsRealWorldReview)
    }
}
