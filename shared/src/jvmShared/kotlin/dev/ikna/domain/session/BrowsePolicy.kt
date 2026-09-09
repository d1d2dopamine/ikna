package dev.ikna.domain.session

import dev.ikna.domain.governor.GovernorReason
import kotlin.math.min

/** A bounded, optional reading pass over material that was learned actively. */
data class BrowsePlan(
    val cards: List<SessionCard>,
    val deckId: String,
    val deckTitle: String
)

/**
 * The rules that keep Browse from becoming an easier replacement for review.
 *
 * Browse opens only after the required part of the one global daily plan is
 * complete. Its allowance is global too: changing decks cannot multiply it.
 * None of these cards is an FSRS answer; this object only decides how many may
 * be read and whether an old card is mature enough to enter the candidate pool.
 */
object BrowsePolicy {
    const val REQUIRED_CARDS_PER_BROWSE = 3
    const val MAX_CARDS_PER_DAY = 6
    const val MIN_ACTIVE_REVIEW_DAYS = 3
    const val MIN_STABILITY_DAYS = 7.0
    const val COOLDOWN_DAYS = 7L
    const val CANDIDATE_SCAN_LIMIT = 240

    /** Explicit extras are appended and never move the required finish line. */
    fun requiredIds(plannedIds: List<String>, extraRequested: Int): List<String> {
        val extra = extraRequested.coerceIn(0, plannedIds.size)
        return plannedIds.take(plannedIds.size - extra).distinct()
    }

    /** Completion is about unique planned questions, not the raw review count. */
    fun requiredComplete(requiredIds: List<String>, answeredIds: Set<String>): Boolean =
        requiredIds.isNotEmpty() && requiredIds.all { it in answeredIds }

    /**
     * One Browse card for every three required questions completed, capped at
     * six. A one-card minimum therefore never opens an unlimited reading feed.
     */
    fun quota(
        requiredCards: Int,
        reason: GovernorReason,
        gate: GovernorReason? = null
    ): Int {
        if (requiredCards < REQUIRED_CARDS_PER_BROWSE) return 0

        // A safety-valve decision keeps the gate it overrode. NO_HEADROOM is a
        // statement about capacity and is safe after the plan is complete; all
        // behavioural gates keep Browse closed.
        val effectiveReason = gate ?: reason
        if (effectiveReason !in setOf(GovernorReason.OK, GovernorReason.NO_HEADROOM)) {
            return 0
        }

        return min(MAX_CARDS_PER_DAY, requiredCards / REQUIRED_CARDS_PER_BROWSE)
    }

    /**
     * Repetitions within one first session do not make a mature card. It must
     * have been retrieved on three different study days, be stable, be outside
     * today's real queue, and not have appeared in Browse during the cooldown.
     */
    fun eligible(
        activeReviewDays: Int,
        stability: Double,
        isNew: Boolean,
        inAmnesty: Boolean,
        dueAt: Long,
        now: Long,
        reviewedToday: Boolean,
        viewedRecently: Boolean
    ): Boolean =
        activeReviewDays >= MIN_ACTIVE_REVIEW_DAYS &&
            stability >= MIN_STABILITY_DAYS &&
            !isNew &&
            !inAmnesty &&
            dueAt > now &&
            !reviewedToday &&
            !viewedRecently
}
