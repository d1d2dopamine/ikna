package dev.ikna.domain.session

import dev.ikna.domain.governor.GovernorReason
import kotlin.math.min

/** A bounded, optional reading pass over material that was learned actively. */
data class BrowsePlan(
    val cards: List<SessionCard>,
    val deckId: String,
    val deckTitle: String
)

/** Every unavailable state has an honest user-facing explanation. */
enum class BrowseUnavailableReason {
    CHECKING,
    CHECK_FAILED,
    PLAN_NOT_COMPLETE,
    NO_CREDITS,
    NO_CANDIDATES,
    LIMIT_REACHED,
    LATE_NIGHT,
    BACKLOG_GUARD,
    POST_SKIP_GUARD,
    LOW_ACTIVITY_GUARD,
    LOW_ACCURACY_GUARD,
    RETURN_GUARD,
    OVERHEATED_GUARD,
    SAFETY_GUARD
}

/** What one deck can say when its always-visible Browse control is pressed. */
data class BrowseAvailability(
    val remaining: Int = 0,
    val reason: BrowseUnavailableReason? = null,
    val additionalReasons: List<BrowseUnavailableReason> = emptyList()
) {
    val blockers: List<BrowseUnavailableReason>
        get() = (listOfNotNull(reason) + additionalReasons).distinct()

    val available: Boolean
        get() = remaining > 0 && blockers.isEmpty()

    companion object {
        fun blocked(reason: BrowseUnavailableReason): BrowseAvailability =
            BrowseAvailability(reason = reason)

        fun blocked(reasons: Iterable<BrowseUnavailableReason>): BrowseAvailability {
            val unique = reasons.distinct()
            return BrowseAvailability(
                reason = unique.firstOrNull() ?: BrowseUnavailableReason.CHECK_FAILED,
                additionalReasons = unique.drop(1)
            )
        }
    }
}

/** Persisted counters used to carry the three-to-one Browse allowance across days. */
data class BrowseCreditLedger(
    val earnedPoints: Int = 0,
    val exposureBaseline: Int? = null,
    val lastExposureCount: Int? = null,
    val creditedDay: String? = null
)

data class BrowseCreditSettlement(
    val ledger: BrowseCreditLedger,
    val availablePoints: Int
)

/**
 * The rules that keep Browse from becoming an easier replacement for review.
 *
 * A completed required card contributes one point. Three points buy one passive
 * card, the remainder carries across days, and the bank never holds more than
 * six cards. The bank is global, so changing decks cannot multiply it.
 */
object BrowsePolicy {
    const val POINTS_PER_BROWSE = 3
    const val MAX_CARDS_PER_DAY = 6
    const val MAX_BANK_POINTS = POINTS_PER_BROWSE * MAX_CARDS_PER_DAY
    const val MIN_SUCCESSFUL_REVIEW_DAYS = 3
    const val COOLDOWN_DAYS = 7L
    const val CANDIDATE_SCAN_LIMIT = 240

    /** Explicit extras are appended and never move the required finish line. */
    fun requiredIds(plannedIds: List<String>, extraRequested: Int): List<String> {
        val extra = extraRequested.coerceIn(0, plannedIds.size)
        return plannedIds.take(plannedIds.size - extra).distinct()
    }

    /** Completion is about unique planned questions, not the raw review count. */
    fun requiredComplete(requiredIds: List<String>, answeredIds: Set<String>): Boolean =
        requiredIds.all { it in answeredIds }

    fun remainingRequired(requiredIds: List<String>, answeredIds: Set<String>): Int =
        requiredIds.count { it !in answeredIds }

    fun cardsForCredits(points: Int): Int =
        min(MAX_CARDS_PER_DAY, points.coerceAtLeast(0) / POINTS_PER_BROWSE)

    /**
     * Reconciles the preference ledger with the append-only exposure log.
     *
     * Existing v9 exposures become the baseline on first use, so updating the
     * app never creates a historical debt. A lower exposure count means the
     * database was wiped while preferences survived a process crash; in that
     * case the ledger safely resets too. ISO day keys only move forward, which
     * prevents changing the system clock backwards from awarding one plan twice.
     */
    fun settleCredits(
        ledger: BrowseCreditLedger,
        day: String,
        completedRequiredCards: Int?,
        totalExposures: Int
    ): BrowseCreditSettlement {
        val exposures = totalExposures.coerceAtLeast(0)
        var baseline = ledger.exposureBaseline ?: exposures
        var lastExposureCount = ledger.lastExposureCount ?: exposures
        var earned = ledger.earnedPoints.coerceAtLeast(0)
        var creditedDay = ledger.creditedDay

        // Baseline can legitimately be zero, so comparing only against it would
        // miss a reset from five exposures back to zero. The last observed count
        // detects every database clear, including progress reset and restore.
        if (exposures < lastExposureCount) {
            baseline = exposures
            earned = 0
            creditedDay = null
        }
        lastExposureCount = exposures

        val exposureDelta = (exposures - baseline)
            .coerceAtLeast(0)
            .coerceAtMost(Int.MAX_VALUE / POINTS_PER_BROWSE)
        val spent = exposureDelta * POINTS_PER_BROWSE
        var available = (earned - spent).coerceIn(0, MAX_BANK_POINTS)

        val mayCredit = completedRequiredCards != null &&
            (creditedDay?.let { previous -> day > previous } ?: true)
        if (mayCredit) {
            available = (available + completedRequiredCards.coerceAtLeast(0))
                .coerceAtMost(MAX_BANK_POINTS)
            creditedDay = day
        }

        // Normalising keeps corrupt or pre-release oversized values from
        // turning into a bank larger than the product contract permits.
        earned = spent + available
        return BrowseCreditSettlement(
            ledger = BrowseCreditLedger(
                earnedPoints = earned,
                exposureBaseline = baseline,
                lastExposureCount = lastExposureCount,
                creditedDay = creditedDay
            ),
            availablePoints = available
        )
    }

    /** FIRST_RUN has no mature cards anyway; it is not a fake safety failure. */
    fun safetyBlock(
        reason: GovernorReason,
        gate: GovernorReason? = null
    ): BrowseUnavailableReason? = when (gate ?: reason) {
        GovernorReason.OK,
        GovernorReason.NO_HEADROOM,
        GovernorReason.FIRST_RUN,
        GovernorReason.PACK_EXHAUSTED -> null
        GovernorReason.BACKLOG_LIMIT -> BrowseUnavailableReason.BACKLOG_GUARD
        GovernorReason.POST_SKIP_WARMUP -> BrowseUnavailableReason.POST_SKIP_GUARD
        GovernorReason.LOW_ACTIVITY -> BrowseUnavailableReason.LOW_ACTIVITY_GUARD
        GovernorReason.LATE_NIGHT -> BrowseUnavailableReason.LATE_NIGHT
        GovernorReason.LOW_ACCURACY -> BrowseUnavailableReason.LOW_ACCURACY_GUARD
        GovernorReason.RETURN_MODE -> BrowseUnavailableReason.RETURN_GUARD
        GovernorReason.OVERHEATED -> BrowseUnavailableReason.OVERHEATED_GUARD
        GovernorReason.SAFETY_VALVE -> BrowseUnavailableReason.SAFETY_GUARD
    }

    /**
     * One first encounter and two successful repetitions on different study
     * days make a familiar card. It must also be outside today's real queue and
     * outside the passive-exposure cooldown.
     */
    fun eligible(
        successfulReviewDays: Int,
        isNew: Boolean,
        inAmnesty: Boolean,
        dueAt: Long,
        now: Long,
        reviewedToday: Boolean,
        viewedRecently: Boolean
    ): Boolean =
        successfulReviewDays >= MIN_SUCCESSFUL_REVIEW_DAYS &&
            !isNew &&
            !inAmnesty &&
            dueAt > now &&
            !reviewedToday &&
            !viewedRecently
}
