package dev.ikna.domain.fsrs

import dev.ikna.data.db.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduler is the one part of this app where a silent regression is
 * invisible in the UI and only shows up as intervals that feel wrong weeks
 * later. These are invariants, not golden numbers: they fail when the model
 * stops being a memory model, not when a constant is retuned.
 */
class FsrsTest {

    private val params = FsrsParams()

    @Test
    fun `the official FSRS 6 parameter set is complete`() {
        assertEquals(21, params.w.size)
        assertEquals(0.212, params.w.first(), 1e-12)
        assertEquals(0.1542, params.w.last(), 1e-12)
    }

    @Test
    fun `a memory just reviewed is fully retrievable`() {
        assertEquals(1.0, Fsrs.retrievability(0.0, 5.0), 1e-9)
    }

    @Test
    fun `stability is still the day on which recall reaches ninety percent`() {
        assertEquals(0.9, Fsrs.retrievability(5.0, 5.0), 1e-12)
        assertEquals(5.0, Fsrs.intervalDays(5.0, 0.9), 1e-12)
    }

    @Test
    fun `retrievability decays with time`() {
        val fresh = Fsrs.retrievability(1.0, 5.0)
        val stale = Fsrs.retrievability(30.0, 5.0)
        assertTrue(stale < fresh)
        assertTrue(stale > 0.0)
    }

    @Test
    fun `an interval is never shorter than a day`() {
        assertTrue(Fsrs.intervalDays(0.01, params.desiredRetention) >= 1.0)
        assertTrue(Fsrs.intervalDays(100.0, params.desiredRetention) > 1.0)
    }

    @Test
    fun `initial difficulty stays inside the model range`() {
        for (rating in Rating.entries) {
            val d = Fsrs.initialDifficulty(rating, params)
            assertTrue(d >= 1.0)
            assertTrue(d <= 10.0)
        }
    }

    @Test
    fun `official FSRS 6 reference vector matches`() {
        // Calculated from py-fsrs 6.3.2 with the official default parameters:
        // S=3, D=5, elapsed=12 days, then each of the four ratings.
        val before = MemoryState(stability = 3.0, difficulty = 5.0)
        val expected = listOf(
            0.8462891714168654 to 8.341762369296838,
            14.100494612014888 to 6.665995369296838,
            21.45775625542881 to 4.9902283692968386,
            37.569531690792616 to 3.3144613692968385
        )

        for ((index, rating) in Rating.entries.withIndex()) {
            val after = Fsrs.next(before, elapsedDays = 12.0, rating = rating, p = params)
            assertEquals(expected[index].first, after.stability, 1e-10)
            assertEquals(expected[index].second, after.difficulty, 1e-10)
        }
    }

    @Test
    fun `same day answers use the FSRS 6 short term model`() {
        val before = MemoryState(stability = 3.0, difficulty = 5.0)

        val again = Fsrs.next(before, elapsedDays = 0.2, rating = Rating.AGAIN, p = params)
        val hard = Fsrs.next(before, elapsedDays = 0.2, rating = Rating.HARD, p = params)
        val good = Fsrs.next(before, elapsedDays = 0.2, rating = Rating.GOOD, p = params)
        val easy = Fsrs.next(before, elapsedDays = 0.2, rating = Rating.EASY, p = params)

        assertEquals(0.9908417942528911, again.stability, 1e-10)
        assertEquals(3.0, hard.stability, 1e-10)
        assertEquals(3.0, good.stability, 1e-10)
        assertEquals(5.044505343174817, easy.stability, 1e-10)
    }

    @Test
    fun `a lapse never strengthens a memory`() {
        val before = MemoryState(stability = 10.0, difficulty = 5.0)
        val after = Fsrs.next(before, elapsedDays = 12.0, rating = Rating.AGAIN, p = params)
        assertTrue(after.stability <= before.stability + 1e-9)
    }

    @Test
    fun `a successful recall never weakens a memory`() {
        val before = MemoryState(stability = 10.0, difficulty = 5.0)
        val after = Fsrs.next(before, elapsedDays = 12.0, rating = Rating.GOOD, p = params)
        assertTrue(after.stability >= before.stability - 1e-9)
    }

    @Test
    fun `answering a new card schedules it into the future`() {
        val now = 1_700_000_000_000L
        val card = CardEntity(
            chunkId = "c-1",
            level = 0,
            stability = 1.0,
            difficulty = 5.0,
            dueAt = now,
            lastReviewAt = null,
            introducedAt = now,
            isNew = true
        )

        val result = Scheduler(params).apply(card, Rating.GOOD, now)

        assertTrue(result.card.dueAt > now)
        assertEquals(1, result.card.reps)
        assertEquals(0, result.card.lapses)
        assertFalse(result.card.isNew)
        assertEquals(now, result.card.lastReviewAt)
    }
}
