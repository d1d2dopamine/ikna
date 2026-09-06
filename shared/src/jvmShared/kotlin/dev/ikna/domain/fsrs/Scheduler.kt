package dev.ikna.domain.fsrs

import dev.ikna.data.db.CardEntity
import dev.ikna.domain.time.DayBoundary
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
 *
 * @param dayStartHour where the study day begins, from `GovernorConfig`. Given
 *   it, due times land on day boundaries instead of clock times -- see [dueAt].
 *   Null keeps raw clock-time scheduling and exists for tests that are about
 *   FSRS arithmetic and nothing else.
 */
class Scheduler(
    private val params: FsrsParams = FsrsParams(),
    private val dayStartHour: Int? = null,
    private val paramsProvider: (() -> FsrsParams)? = null
) {

    private val boundary = dayStartHour?.let { DayBoundary(it) }

    fun currentParameters(): FsrsParams = paramsProvider?.invoke() ?: params
    fun snapshot(): Scheduler = if (paramsProvider == null) this else withParameters(currentParameters())
    fun defaultSnapshot(): Scheduler = if (paramsProvider == null) this else Scheduler(params, dayStartHour)
    fun withParameters(parameters: FsrsParams): Scheduler = Scheduler(parameters, dayStartHour)

    fun apply(card: CardEntity, rating: Rating, now: Long): ScheduleResult {
        if (paramsProvider != null) return snapshot().apply(card, rating, now)
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

        val interval = Fsrs.intervalDays(after.stability, params.desiredRetention, params)
        val updated = card.copy(
            stability = after.stability,
            difficulty = after.difficulty,
            dueAt = dueAt(now, interval),
            lastReviewAt = now,
            reps = card.reps + 1,
            lapses = card.lapses + if (rating == Rating.AGAIN) 1 else 0,
            inAmnesty = false,
            isNew = false
        )
        return ScheduleResult(updated, elapsedDays, before, after)
    }

    /** Predicted recall BEFORE consuming the next outcome. */
    fun predictRecall(card: CardEntity, now: Long): Double {
        if (paramsProvider != null) return snapshot().predictRecall(card, now)
        val elapsed = card.lastReviewAt?.let { (now - it).toDouble() / DAY_MS } ?: 0.0
        return Fsrs.retrievability(elapsed.coerceAtLeast(0.0), card.stability, params)
    }

    /**
     * Version 1's bounded refinement. Cap stability AND the actual due delay
     * against GOOD from the same before-state. Day-boundary rounding may make
     * a small refinement impossible; in that case keep GOOD's due date rather
     * than breaking either the 30% limit or the study-day boundary.
     */
    fun applyDerivedV1(card: CardEntity, rating: Rating, now: Long): ScheduleResult {
        if (paramsProvider != null) return snapshot().applyDerivedV1(card, rating, now)
        require(rating == Rating.HARD || rating == Rating.EASY)
        val good = apply(card, Rating.GOOD, now)
        val raw = apply(card, rating, now)
        val low = if (rating == Rating.HARD) 0.7 else 1.0
        val high = if (rating == Rating.EASY) 1.3 else 1.0
        val after = boundDerivedMemoryV1(good.after, raw.after, rating)
        val stability = after.stability
        val interval = Fsrs.intervalDays(stability, params.desiredRetention, params)
        val candidate = dueAt(now, interval)
        val baseDelay = (good.card.dueAt - now).coerceAtLeast(0L)
        val minDelay = kotlin.math.ceil(baseDelay * low).toLong()
        val maxDelay = kotlin.math.floor(baseDelay * high).toLong()
        val delay = candidate - now
        val boundedDue = if (delay in minDelay..maxDelay) candidate else good.card.dueAt
        return raw.copy(after = after, card = raw.card.copy(stability = stability, dueAt = boundedDue))
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
        if (paramsProvider != null) return snapshot().introduce(chunkId, level, componentPrior, now)
        val baseS = Fsrs.initialStability(Rating.GOOD, params)
        val stability = baseS * (1.0 + componentPrior.knownRatio * 2.0)
        val difficulty = min(
            10.0,
            max(1.0, Fsrs.initialDifficulty(Rating.GOOD, params) - componentPrior.knownRatio * 1.5)
        )
        val interval = if (componentPrior.knownRatio >= 0.75) {
            Fsrs.intervalDays(stability, params.desiredRetention, params)
        } else {
            0.0
        }
        return CardEntity(
            chunkId = chunkId,
            level = level,
            stability = stability,
            difficulty = difficulty,
            // A chunk the learner does not know yet is due now, so it reaches
            // today's plan; one whose words are already known gets a real
            // interval and therefore a real day boundary.
            dueAt = if (interval > 0.0) dueAt(now, interval) else now,
            lastReviewAt = null,
            introducedAt = now,
            isNew = true
        )
    }

    /**
     * When a card with this interval comes back.
     *
     * An interval is a number of days, and every counter in this app is keyed by
     * a study day that starts at 04:00. "Three days" therefore has to mean
     * "waiting on the third day", not "at 23:47 on the third day". It used to
     * mean the second thing: answering at 23:00 set the next due time to 23:00,
     * so somebody who studies in the evening met that card a day late every
     * time, and the interval FSRS had chosen was quietly stretched by up to a
     * full day on every single review -- compounding across a card's whole
     * history, in the direction of forgetting.
     *
     * Snapping to the start of the day the interval lands in can only move a due
     * time earlier, never later, so no card is ever hidden for longer than the
     * scheduler intended. It also cannot move one into the past: intervals are
     * floored at one full day by [Fsrs.intervalDays], and a day start is at most
     * 24 hours behind the moment it precedes. The [max] is there so that this
     * stays true if that floor is ever lowered.
     */
    private fun dueAt(now: Long, intervalDays: Double): Long {
        val target = now + (intervalDays * DAY_MS).roundToLong()
        val dayStart = boundary?.startOfDay(target) ?: return target
        return max(dayStart, now)
    }
}

/** How much of a chunk the learner already knows, from the component layer. */
data class ComponentPrior(
    val knownRatio: Double,
    val unknownContentTokens: Int,
    val weakLemmas: List<String>
)
