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
    fun `a memory just reviewed is fully retrievable`() {
        assertEquals(1.0, Fsrs.retrievability(0.0, 5.0), 1e-9)
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
