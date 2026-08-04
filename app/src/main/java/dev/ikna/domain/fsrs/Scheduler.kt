package dev.ikna.domain.fsrs

import dev.ikna.data.db.CardEntity
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

const val DAY_MS = 86_400_000L

data class ScheduleResult(
    val card: CardEntity,
    val elapsedDays: Double,
    val before: MemoryState,
    val after: MemoryState
)

/**
 * Applies a rating to a card.
 *
 * Overdue cards are not punished for waiting: FSRS recomputes retrievability
 * from the real elapsed time, so a long gap simply produces a shorter next
 * interval. That is what makes amnesty honest rather than lenient.
 */
class Scheduler(private val params: FsrsParams = FsrsParams()) {

    fun apply(card: CardEntity, rating: Rating, now: Long): ScheduleResult {
        val before = MemoryState(card.stability, card.difficulty)
        val elapsedDays = card.lastReviewAt
            ?.let { (now - it).toDouble() / DAY_MS }
            ?.coerceAtLeast(0.0)
            ?: 0.0

        val after = if (card.isNew) {
            // First answer. The prior baked in at introduction time is replaced
            // by the real observation, but the prior already decided which
            // chunks were worth introducing at all.
            Fsrs.initial(rating, params)
        } else {
            Fsrs.next(before, elapsedDays, rating, params)
        }

        val interval = Fsrs.intervalDays(after.stability, params.desiredRetention)
        val updated = card.copy(
            stability = after.stability,
            difficulty = after.difficulty,
            dueAt = now + (interval * DAY_MS).roundToLong(),
            lastReviewAt = now,
            reps = card.reps + 1,
            lapses = card.lapses + if (rating == Rating.AGAIN) 1 else 0,
            inAmnesty = false,
            isNew = false
        )
        return ScheduleResult(updated, elapsedDays, before, after)
    }

    /**
     * Creates a card for a freshly introduced chunk, seeded from the component
     * layer. A chunk whose words are already known starts with a real interval
     * instead of ten minutes, which is where most of the review-count saving
     * comes from.
     */
    fun introduce(
        chunkId: String,
        level: Int,
        componentPrior: ComponentPrior,
        now: Long
    ): CardEntity {
        val baseS = Fsrs.initialStability(Rating.GOOD, params)
        val stability = baseS * (1.0 + componentPrior.knownRatio * 2.0)
        val difficulty = min(
            10.0,
            max(1.0, Fsrs.initialDifficulty(Rating.GOOD, params) - componentPrior.knownRatio * 1.5)
        )
        val interval = if (componentPrior.knownRatio >= 0.75) {
            Fsrs.intervalDays(stability, params.desiredRetention)
        } else {
            0.0
        }
        return CardEntity(
            chunkId = chunkId,
            level = level,
            stability = stability,
            difficulty = difficulty,
            dueAt = now + (interval * DAY_MS).toLong(),
            lastReviewAt = null,
            introducedAt = now,
            isNew = true
        )
    }
}

/** How much of a chunk the learner already knows, from the component layer. */
data class ComponentPrior(
    val knownRatio: Double,
    val unknownContentTokens: Int,
    val weakLemmas: List<String>
)
