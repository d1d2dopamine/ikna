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
    /** Below this share of the last week actually used, no new chunks appear. */
    val minActivityRatio: Double = 0.5,
    /** Share of the daily norm that already counts as a day fully used. */
    val idleCreditRatio: Double = 0.3,
    /** Window for the activity signal, in completed days. */
    val activityWindowDays: Int = 7,
    /**
     * How many days inside that window a normal week is expected to contain.
     * Four or five, not seven: the norm is weekly on purpose, so an ordinary
     * week with three quiet days still reads as a week that went fine.
     */
    val activeDaysPerWeek: Double = 4.5,
    /**
     * After this hour nothing new is introduced today. Only introductions wait;
     * reviews stay available all night.
     */
    val nightCutoffHour: Int = 23,
    /**
     * The hour a day rolls over. Not midnight, because midnight is the
     * middle of the evening for a good share of the people this app is for:
     * a session finished at 01:30 landed on the next day, showed up as a
     * hole in the activity map, and built tomorrow's plan as if last night
     * had never happened.
     */
    val dayStartHour: Int = 4,
    /** Days from the first session during which the load ceiling stays flat. */
    val settlingDays: Int = 60,
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
    val desiredRetention: Double = 0.9,
    /**
     * How overdue a card has to be before it leaves the visible queue for the
     * amnesty pool.
     *
     * This was a `2` written into the repository, next to the one line that
     * decides what a returning user sees: every load-bearing number in this app
     * is in this file, and one of them was not. Two days is unchanged -- it is
     * long enough that an ordinary evening off does not hide anything, and short
     * enough that a real absence never comes back as a wall of cards.
     */
    val amnestyAfterDays: Int = 2
) {
    companion object {
        // Built once. A Json instance compiles its configuration on creation, so
        // making a fresh one inside load() paid that cost every call for nothing.
        // ignoreUnknownKeys is what lets an older build read a governor.json
        // written by a newer one instead of falling back to the defaults.
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): GovernorConfig = runCatching {
            val text = context.assets.open("governor.json").bufferedReader().use { it.readText() }
            json.decodeFromString<GovernorConfig>(text)
        }.getOrElse { GovernorConfig() }
    }
}
