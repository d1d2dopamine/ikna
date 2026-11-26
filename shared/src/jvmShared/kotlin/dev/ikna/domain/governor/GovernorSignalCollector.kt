package dev.ikna.domain.governor

import dev.ikna.data.db.DailyStatEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Pure, database-free statistical signal calculations for [RiarManage].
 *
 * Extracted from `LearningRepository` so overheating detection, return-gap
 * scanning, activity ratio, and measured-norm calculation can be unit-tested
 * directly without SQLite or coroutine scaffolding.
 */
object GovernorSignalCollector {

    const val AUTO_COLD_START = 25
    const val AUTO_MIN = 12
    const val AUTO_MAX = 80
    const val AUTO_HEADROOM = 1.15

    /**
     * Whether the last completed study day ran hot: well above the prior
     * baseline median volume, paired with either a meaningful accuracy drop
     * below the prior baseline average or an unfinished daily plan.
     *
     * Excluding `last` (and `today`) from `baseline` prevents the outlier day
     * itself from inflating the median or diluting its own accuracy drop.
     */
    fun isOverheated(
        recentActiveDays: List<DailyStatEntity>,
        todayKey: String,
        overheatRatio: Double,
        overheatAccuracyDrop: Double
    ): Boolean {
        val days = recentActiveDays.filter { it.reviewsDone > 0 }
        if (days.size < 4) return false

        val last = days.firstOrNull { it.day != todayKey } ?: return false
        val baseline = days.filter { it.day != todayKey && it.day != last.day }
        if (baseline.size < 3) return false

        val counts = baseline.map { it.reviewsDone }.sorted()
        val median = counts[counts.size / 2]
        if (median <= 0) return false
        if (last.reviewsDone <= median * overheatRatio) return false

        val usualAccuracy = baseline.map { it.accuracy }.average()
        val sloppy = last.accuracy < usualAccuracy - overheatAccuracyDrop
        return sloppy || !last.planCompleted
    }

    /**
     * Days elapsed since the first session after the most recent gap of at
     * least [returnModeGapDays], or null when no qualifying absence occurred.
     * Input [recentDaysNewestFirst] is ordered newest day first.
     */
    fun daysSinceReturn(
        recentDaysNewestFirst: List<DailyStatEntity>,
        todayKey: String,
        returnModeGapDays: Int
    ): Int? {
        val active = recentDaysNewestFirst
            .filter { it.reviewsDone > 0 }
            .mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }
        if (active.size < 2) return null

        val today = runCatching { LocalDate.parse(todayKey) }.getOrNull() ?: return null
        for (i in 0 until active.size - 1) {
            val gap = ChronoUnit.DAYS.between(active[i + 1], active[i]).toInt()
            if (gap >= returnModeGapDays) {
                return ChronoUnit.DAYS.between(active[i], today).toInt().coerceAtLeast(0)
            }
        }
        return null
    }

    /** Measured daily target: median of active days over the window plus headroom. */
    fun computeAutoTarget(recentDays: List<DailyStatEntity>): Int {
        val done = recentDays
            .map { it.reviewsDone }
            .filter { it > 0 }
            .sorted()
        if (done.size < 3) return AUTO_COLD_START
        val median = done[done.size / 2]
        return (median * AUTO_HEADROOM).toInt().coerceIn(AUTO_MIN, AUTO_MAX)
    }

    /** Fraction of the weekly activity norm actually used, in `0.0..1.0`. */
    fun computeActivityRatio(
        recentDays: List<DailyStatEntity>,
        todayKey: String,
        cutoffKey: String,
        windowDays: Int,
        activeDaysPerWeek: Double,
        expectedForCredit: Double
    ): Double {
        if (recentDays.isEmpty()) return 1.0
        val inWindow = recentDays.filter { it.day >= cutoffKey }
        val today = LocalDate.parse(todayKey)
        val firstEver = runCatching { LocalDate.parse(recentDays.last().day) }.getOrNull()
        val span = if (firstEver == null) windowDays
        else (ChronoUnit.DAYS.between(firstEver, today).toInt() + 1).coerceIn(1, windowDays)

        val safeExpected = max(1.0, expectedForCredit)
        val used = inWindow.sumOf { (it.reviewsDone / safeExpected).coerceIn(0.0, 1.0) }
        val need = minOf(span.toDouble(), activeDaysPerWeek).coerceAtLeast(1.0)
        return (used / need).coerceIn(0.0, 1.0)
    }
}
