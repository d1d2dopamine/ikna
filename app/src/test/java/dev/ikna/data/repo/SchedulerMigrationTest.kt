package dev.ikna.data.repo

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.domain.fsrs.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerMigrationTest {

    @Test
    fun `replay changes only cards that still exist`() {
        val firstTs = 1_700_000_000_000L
        val reviewed = card("kept", isNew = false, reps = 2)
        val untouched = card("new", isNew = true, reps = 0)
        val answers = listOf(
            answer("kept", firstTs, rating = 3, wasNew = true),
            answer("kept", firstTs + 10 * 60_000L, rating = 3, wasNew = false),
            // Its deck was deleted. The append-only log keeps the answer for
            // statistics, but migration must not resurrect its card.
            answer("deleted", firstTs, rating = 4, wasNew = true)
        )

        val replay = replayCardsForFsrs6(
            currentCards = listOf(reviewed, untouched),
            answers = answers,
            scheduler = Scheduler()
        )

        assertEquals(2, replay.cards.size)
        assertEquals(1, replay.replayedCards)
        assertEquals(2, replay.replayedAnswers)
        assertFalse(replay.cards.any { it.chunkId == "deleted" })
        assertEquals(untouched, replay.cards.single { it.chunkId == "new" })

        val migrated = replay.cards.single { it.chunkId == "kept" }
        assertEquals(2.3065, migrated.stability, 1e-10)
        assertEquals(2.1112142357853942, migrated.difficulty, 1e-10)
        assertEquals(2, migrated.reps)
        assertTrue(migrated.dueAt > firstTs)
        assertEquals(reviewed.introducedAt, migrated.introducedAt)
    }

    @Test
    fun `replaying the same immutable log is idempotent`() {
        val ts = 1_700_000_000_000L
        val current = card("one", isNew = false, reps = 1)
        val answers = listOf(answer("one", ts, rating = 3, wasNew = true))
        val scheduler = Scheduler()

        val first = replayCardsForFsrs6(listOf(current), answers, scheduler).cards
        val second = replayCardsForFsrs6(first, answers, scheduler).cards

        assertEquals(first, second)
    }

    private fun card(chunkId: String, isNew: Boolean, reps: Int) = CardEntity(
        chunkId = chunkId,
        level = 0,
        stability = 99.0,
        difficulty = 9.0,
        dueAt = 1_800_000_000_000L,
        lastReviewAt = if (isNew) null else 1_700_000_000_000L,
        introducedAt = 1_690_000_000_000L,
        reps = reps,
        lapses = 0,
        isNew = isNew
    )

    private fun answer(
        chunkId: String,
        ts: Long,
        rating: Int,
        wasNew: Boolean
    ) = ReviewEntity(
        chunkId = chunkId,
        level = 0,
        ts = ts,
        rating = rating,
        elapsedDays = 0.0,
        stabilityBefore = 1.0,
        stabilityAfter = 1.0,
        difficultyBefore = 5.0,
        difficultyAfter = 5.0,
        durationMs = 500L,
        wasAmnesty = false,
        prevStability = 1.0,
        prevDifficulty = 5.0,
        prevDueAt = ts,
        prevLastReviewAt = if (wasNew) null else ts - 10 * 60_000L,
        prevReps = if (wasNew) 0 else 1,
        prevLapses = 0,
        prevIsNew = wasNew,
        prevInAmnesty = false
    )
}
