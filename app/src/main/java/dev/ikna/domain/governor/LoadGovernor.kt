package dev.ikna.domain.governor

import kotlin.math.max
import kotlin.math.roundToInt

data class GovernorSignals(
    val dueToday: Int,
    val forecastAvg3d: Double,
    val backlog: Int,
    val accuracyRecent: Double,
    val daysSinceLastSession: Int,
    val reviewsDoneToday: Int,
    val cleanDays: Int,
    val newIntroducedLastWeek: Int,
    val totalReviews: Int
)

enum class GovernorReason {
    FIRST_RUN,
    OK,
    NO_HEADROOM,
    BACKLOG_LIMIT,
    POST_SKIP_WARMUP,
    LOW_ACCURACY,
    RETURN_MODE,
    SAFETY_VALVE
}

data class GovernorDecision(
    val allowedNew: Int,
    val capacity: Int,
    val projected: Double,
    val headroom: Double,
    val amnestyQuota: Int,
    val reason: GovernorReason
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
        val inReturnMode = s.daysSinceLastSession >= config.returnModeGapDays
        val capacity = if (inReturnMode) config.returnModeCapacity else config.targetDailyReviews
        val amnestyQuota = (capacity * config.amnestyQuotaRatio).roundToInt()

        val projected = max(s.dueToday.toDouble(), s.forecastAvg3d) +
            config.backlogWeight * s.backlog
        val headroom = capacity - projected

        fun blocked(reason: GovernorReason) = GovernorDecision(
            allowedNew = 0,
            capacity = capacity,
            projected = projected,
            headroom = headroom,
            amnestyQuota = amnestyQuota,
            reason = reason
        )

        // Cold start: nothing learned yet, hand out a first batch unconditionally.
        if (s.totalReviews == 0) {
            return GovernorDecision(
                allowedNew = config.maxNewPerDay,
                capacity = capacity,
                projected = projected,
                headroom = headroom,
                amnestyQuota = amnestyQuota,
                reason = GovernorReason.FIRST_RUN
            )
        }

        // ---- hard gates -----------------------------------------------------
        // Each of these can be overridden only by the safety valve below.
        val gate: GovernorReason? = when {
            inReturnMode -> GovernorReason.RETURN_MODE
            s.backlog > config.backlogHardLimit -> GovernorReason.BACKLOG_LIMIT
            // New material is earned by attention after a skipped day, never
            // handed out to help the user "catch up".
            s.daysSinceLastSession >= 1 &&
                s.reviewsDoneToday < config.warmupReviewsAfterSkip -> GovernorReason.POST_SKIP_WARMUP
            s.accuracyRecent < config.minAccuracy -> GovernorReason.LOW_ACCURACY
            else -> null
        }

        if (gate != null) {
            return if (safetyValveOpen(s)) {
                blocked(GovernorReason.SAFETY_VALVE).copy(allowedNew = 1)
            } else {
                blocked(gate)
            }
        }

        // ---- normal path ----------------------------------------------------
        val ceiling = effectiveMaxNew(s)
        val allowed = (headroom / config.costPerNew)
            .toInt()
            .coerceIn(0, ceiling)

        if (allowed == 0) {
            return if (safetyValveOpen(s)) {
                blocked(GovernorReason.SAFETY_VALVE).copy(allowedNew = 1)
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
            reason = GovernorReason.OK
        )
    }

    /**
     * Without this the governor can latch at zero forever: novelty disappears,
     * the app becomes the treadmill it was built to avoid, and the user leaves.
     * One chunk a week, no matter what.
     */
    private fun safetyValveOpen(s: GovernorSignals): Boolean =
        s.newIntroducedLastWeek == 0

    /** Load rises to meet current form without being asked. */
    private fun effectiveMaxNew(s: GovernorSignals): Int {
        if (s.cleanDays < config.accelerateAfterCleanDays || s.accuracyRecent < 0.9) {
            return config.maxNewPerDay
        }
        val steps = s.cleanDays / config.accelerateAfterCleanDays
        return (config.maxNewPerDay + steps * config.accelerateStep)
            .coerceAtMost(config.maxNewCeiling)
    }
}
