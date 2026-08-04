package dev.ikna.domain.governor

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Loaded from `assets/governor.json`. Intentionally not exposed anywhere in the
 * UI: a settings screen turns this app into a tuning toy, and tuning is more
 * interesting than studying.
 */
@Serializable
data class GovernorConfig(
    val targetDailyReviews: Int = 40,
    val maxNewPerDay: Int = 6,
    val maxNewCeiling: Int = 20,
    val costPerNew: Double = 4.0,
    val backlogWeight: Double = 0.5,
    val backlogHardLimit: Int = 120,
    val minAccuracy: Double = 0.75,
    val warmupReviewsAfterSkip: Int = 10,
    val amnestyQuotaRatio: Double = 0.2,
    val forecastHorizonDays: Int = 3,
    val recentWindowSize: Int = 100,
    val safetyValveDays: Int = 7,
    val accelerateAfterCleanDays: Int = 5,
    val accelerateStep: Int = 2,
    val returnModeGapDays: Int = 14,
    val returnModeDays: Int = 3,
    val returnModeCapacity: Int = 10,
    val dailyMinimumCards: Int = 1,
    val desiredRetention: Double = 0.9
) {
    companion object {
        fun load(context: Context): GovernorConfig = runCatching {
            val text = context.assets.open("governor.json").bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<GovernorConfig>(text)
        }.getOrElse { GovernorConfig() }
    }
}
