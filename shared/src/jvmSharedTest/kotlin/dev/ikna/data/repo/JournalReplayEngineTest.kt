package dev.ikna.data.repo

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Scheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalReplayEngineTest {

    @Test
    fun `full history replay and existing card replay produce identical schedules`() {
        val scheduler = Scheduler(dayStartHour = 4)
        val t0 = 1_700_000_000_000L
        val r1 = ReviewEntity(
            id = 1L,
            chunkId = "en-1",
            level = 0,
            ts = t0,
            rating = 3,
            elapsedDays = 0.0,
            stabilityBefore = 0.0,
            stabilityAfter = 2.0,
            difficultyBefore = 5.0,
            difficultyAfter = 5.0,
            durationMs = 2_400L,
            wasAmnesty = false,
            prevStability = 0.0,
            prevDifficulty = 5.0,
            prevDueAt = t0,
            prevLastReviewAt = null,
            prevReps = 0,
            prevLapses = 0,
            prevIsNew = true
        )
        val r2 = ReviewEntity(
            id = 2L,
            chunkId = "en-1",
            level = 0,
            ts = t0 + 2 * DAY_MS,
            rating = 3,
            elapsedDays = 2.0,
            stabilityBefore = 2.0,
            stabilityAfter = 6.0,
            difficultyBefore = 5.0,
            difficultyAfter = 5.0,
            durationMs = 1_800L,
            wasAmnesty = false,
            prevStability = 2.0,
            prevDifficulty = 5.0,
            prevDueAt = t0 + 2 * DAY_MS,
            prevLastReviewAt = t0,
            prevReps = 1,
            prevLapses = 0,
            prevIsNew = false
        )

        val full = JournalReplayEngine.replayFullHistory(
            answers = listOf(r1, r2),
            scheduler = scheduler,
            dayStartHour = 4
        )
        val existingSeed = CardEntity(
            chunkId = "en-1",
            level = 0,
            stability = 0.0,
            difficulty = 5.0,
            dueAt = t0,
            lastReviewAt = null,
            introducedAt = t0,
            reps = 0,
            lapses = 0,
            inAmnesty = false,
            isNew = true
        )
        val migrated = JournalReplayEngine.replayExistingCards(
            currentCards = listOf(existingSeed),
            answers = listOf(r1, r2),
            scheduler = scheduler
        )

        val fullCard = full.cards.getValue("en-1:0")
        val migratedCard = migrated.cards.single()
        assertEquals(fullCard.stability, migratedCard.stability, 1e-9)
        assertEquals(fullCard.difficulty, migratedCard.difficulty, 1e-9)
        assertEquals(fullCard.dueAt, migratedCard.dueAt)
        assertEquals(fullCard.reps, migratedCard.reps)
        assertEquals(2, full.replayedAnswers)
        assertEquals(2, migrated.replayedAnswers)
        assertTrue(full.days.values.sumOf { it.reviewsDone } == 2)
    }
}
