package dev.ikna.domain.governor

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GovernorSignals(
    val dueToday: Int,
    val forecastAvg3d: Double,
    val backlog: Int,
    val accuracyRecent: Double,
    /**
     * How much of the last week was actually used, 0..1: work done divided by
     * work a day is expected to hold. This is the graded version of "did the
     * user show up" — a day missed, a day of two cards and a normal day are
     * three different numbers, not two states.
     */
    val activityRatio: Double,
    val daysSinceLastSession: Int,
    /**
     * Days since the very first session. The ceiling does not rise inside the
     * settling window: a routine needs months to become automatic, and the
     * fastest way to lose one is to make it heavier while it is still fragile.
     */
    val daysSinceStart: Int,
    val reviewsDoneToday: Int,
    val cleanDays: Int,
    val newIntroducedLastWeek: Int,
    val totalReviews: Int,
    /**
     * Days since the first session after the last real absence, or null when
     * the history has no such gap.
     *
     * Coming back is not one day. Being handed a full day of new material on
     * the second evening after two weeks away is how a return turns into the
     * next absence, so the softer capacity has to outlive the single session
     * that ended the gap.
     */
    val daysSinceReturn: Int? = null,
    /**
     * Whether the recent history looks like a day that cost more than it
     * gave: well above the usual size, and either sloppier than usual or
     * abandoned before the plan ended.
     *
     * This is the signal the app was missing. Everything else here protects
     * against a queue growing too fast; nothing protected against the user
     * being spent. One heroic evening is how the following four days get
     * skipped, and the median cannot see it because a median is exactly what
     * one outlier does not move.
     */
    val overheated: Boolean = false
)

enum class GovernorReason {
    FIRST_RUN,
    OK,
    NO_HEADROOM,
    BACKLOG_LIMIT,
    POST_SKIP_WARMUP,
    /** The last week was mostly empty. Nothing new until attention comes back. */
    LOW_ACTIVITY,
    /** Too late in the day to meet something new. Reviews are unaffected. */
    LATE_NIGHT,
    LOW_ACCURACY,
    RETURN_MODE,
    SAFETY_VALVE,

    /**
     * The last day ran hot. Today is deliberately smaller, and nothing new
     * arrives in it.
     */
    OVERHEATED,

    /**
     * Nothing left in the deck to meet for the first time.
     *
     * Not a governor decision — the governor rules on how much new material is
     * allowed, never on how much exists — so it is set by the repository, which
     * is the layer that can count. It is here because the screen reads one enum.
     */
    PACK_EXHAUSTED
}

data class GovernorDecision(
    val allowedNew: Int,
    val capacity: Int,
    val projected: Double,
    val headroom: Double,
    val amnestyQuota: Int,
    val reason: GovernorReason,
    /**
     * The hard daily ceiling on new material, whatever the headroom said. Stored
     * on the decision so the session can tell "nothing fits today" apart from
     * "nothing more is coming today", which are different sentences.
     */
    val newCeiling: Int = 0,
    /**
     * What actually stopped the new material, when [reason] is [GovernorReason.SAFETY_VALVE].
     *
     * The valve used to overwrite the reason it was overriding, so the one
     * situation that most needs explaining -- something is wrong, and one chunk
     * is being handed out anyway -- was recorded as "safety valve" and nothing
     * else. The log then had no way of showing that the valve opens every week
     * because accuracy is low, which is the whole point of watching it.
     */
    val gate: GovernorReason? = null
)

/**
 * Decides how many new chunks may be introduced today. Nothing else.
 *
 * The single idea other apps miss: a new chunk is not one card today, it is
 * roughly `costPerNew` reviews spread over the following week. Treating new
 * material as free is what produces the avalanche three weeks in, and the
 * avalanche is what ends the habit.
 */
class LoadGovernor(private val config: GovernorConfig) {

    fun decide(s: GovernorSignals): GovernorDecision {
        // Both halves of return mode: the day the user comes back, and the few
        // days after it. `returnModeDays` was declared, documented and never
        // read, so the softer capacity used to disappear the moment a single
        // session happened.
        val settlingBackIn = s.daysSinceReturn?.let { it < config.returnModeDays } ?: false
        val inReturnMode = s.daysSinceLastSession >= config.returnModeGapDays || settlingBackIn
        val normal = if (inReturnMode) config.returnModeCapacity else config.targetDailyReviews

        // A day after an overheated one is shorter, and the shortening is the
        // point: the queue is not the thing at risk here, the person is.
        val capacity = if (s.overheated) {
            (normal * config.overheatCapacityShare)
                .roundToInt()
                .coerceAtLeast(config.dailyMinimumCards)
        } else {
            normal
        }
        val amnestyQuota = (capacity * config.amnestyQuotaRatio).roundToInt()
        val newCeiling = dailyNewCeiling(capacity)

        val projected = max(s.dueToday.toDouble(), s.forecastAvg3d) +
            config.backlogWeight * s.backlog
        val headroom = capacity - projected

        fun blocked(reason: GovernorReason) = GovernorDecision(
            allowedNew = 0,
            capacity = capacity,
            projected = projected,
            headroom = headroom,
            amnestyQuota = amnestyQuota,
            reason = reason,
            newCeiling = newCeiling
        )

        // Cold start: nothing learned yet, hand out a first batch unconditionally.
        if (s.totalReviews == 0) {
            return GovernorDecision(
                allowedNew = config.maxNewPerDay,
                capacity = capacity,
                projected = projected,
                headroom = headroom,
                amnestyQuota = amnestyQuota,
                reason = GovernorReason.FIRST_RUN,
                newCeiling = newCeiling
            )
        }

        // ---- hard gates -----------------------------------------------------
        // Each of these can be overridden only by the safety valve below.
        val gate: GovernorReason? = when {
            inReturnMode -> GovernorReason.RETURN_MODE
            // Nothing new the day after a day that ran hot. Reviews still
            // arrive, in a smaller plan.
            s.overheated -> GovernorReason.OVERHEATED
            s.backlog > config.backlogHardLimit -> GovernorReason.BACKLOG_LIMIT
            // Barely being here counts as being away. Adding chunks to a week
            // that is already going badly is how the pile that ends the habit
            // gets built, and it gets built while nobody is watching.
            s.activityRatio < config.minActivityRatio -> GovernorReason.LOW_ACTIVITY
            // New material is earned by attention after a skipped day, never
            // handed out to help the user "catch up".
            //
            // Two days, not one. The plan for a day is built once, before the
            // first answer of that day exists, so `daysSinceLastSession >= 1`
            // was true every ordinary morning that followed an ordinary
            // evening: the gate fired on every single day the app was used
            // normally, `allowedNew` was zero, and the safety valve — one chunk
            // a week, meant as the last resort — became the only way new
            // material ever arrived. A skipped day is a day with no answers in
            // it, which is two calendar days since the last one.
            s.daysSinceLastSession >= 2 &&
                s.reviewsDoneToday < config.warmupReviewsAfterSkip -> GovernorReason.POST_SKIP_WARMUP
            s.accuracyRecent < config.minAccuracy -> GovernorReason.LOW_ACCURACY
            else -> null
        }

        if (gate != null) {
            return if (safetyValveOpen(s)) {
                blocked(GovernorReason.SAFETY_VALVE).copy(allowedNew = 1, gate = gate)
            } else {
                blocked(gate)
            }
        }

        // ---- normal path ----------------------------------------------------
        // Two ceilings, and the lower one wins: what current form deserves, and
        // what a single day is allowed to spend at all.
        val ceiling = min(effectiveMaxNew(s), newCeiling)
        val allowed = (headroom / config.costPerNew)
            .toInt()
            .coerceIn(0, ceiling)

        if (allowed == 0) {
            return if (safetyValveOpen(s)) {
                blocked(GovernorReason.SAFETY_VALVE).copy(
                    allowedNew = 1,
                    gate = GovernorReason.NO_HEADROOM
                )
            } else {
                blocked(GovernorReason.NO_HEADROOM)
            }
        }

        return GovernorDecision(
            allowedNew = allowed,
            capacity = capacity,
            projected = projected,
            headroom = headroom,
            amnestyQuota = amnestyQuota,
            reason = GovernorReason.OK,
            newCeiling = newCeiling
        )
    }

    /**
     * The hard daily ceiling on new material.
     *
     * Scaled to the day's capacity rather than fixed, and never zero: a day with
     * nothing new in it at all is the day the app becomes a chore, and the whole
     * argument for this app is that one unfamiliar thing is what makes tomorrow
     * worth opening.
     */
    fun dailyNewCeiling(capacity: Int): Int =
        (capacity * config.newCeilingShare)
            .roundToInt()
            .coerceIn(1, config.maxNewCeiling)

    /**
     * How much new material a session has earned by going past the day's
     * obligation.
     *
     * The old arithmetic ran once, before the first answer of the day: with the
     * queue already filling the capacity there was no headroom, so `allowedNew`
     * was zero and stayed zero however much work the user actually did. A day
     * that turns out lighter than predicted could not be spent, and "finish the
     * plan and get nothing new" is the treadmill this app exists to avoid.
     *
     * The price is [GovernorConfig.earnedNewPerReviews] reviews per chunk, which
     * is what the chunk will cost over the coming week -- so it is paid, not
     * borrowed. The daily ceiling still applies on top of it.
     */
    fun earnedNew(reviewsPastObligation: Int): Int =
        if (reviewsPastObligation <= 0) 0
        else reviewsPastObligation / config.earnedNewPerReviews

    /**
     * Without this the governor can latch at zero forever: novelty disappears,
     * the app becomes the treadmill it was built to avoid, and the user leaves.
     * One chunk per `safetyValveDays`, no matter what.
     *
     * The window itself lives in the caller: `newIntroducedLastWeek` is counted
     * over `config.safetyValveDays`. This is a last resort and nothing else — if
     * it is opening every week, a gate above is wrong.
     */
    private fun safetyValveOpen(s: GovernorSignals): Boolean =
        s.newIntroducedLastWeek == 0

    /** Load rises to meet current form without being asked. */
    private fun effectiveMaxNew(s: GovernorSignals): Int {
        // Flat for the first two months. That is roughly how long a daily
        // routine takes to stop needing willpower, and the acceleration below
        // is exactly what turns one good week into a week nobody can repeat.
        if (s.daysSinceStart < config.settlingDays) return config.maxNewPerDay
        if (s.cleanDays < config.accelerateAfterCleanDays ||
            s.accuracyRecent < config.accelerateMinAccuracy
        ) {
            return config.maxNewPerDay
        }
        val steps = s.cleanDays / config.accelerateAfterCleanDays
        return (config.maxNewPerDay + steps * config.accelerateStep)
            .coerceAtMost(config.maxNewCeiling)
    }
}
