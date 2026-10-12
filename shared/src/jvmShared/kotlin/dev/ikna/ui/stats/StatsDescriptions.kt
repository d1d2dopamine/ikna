package dev.ikna.ui.stats

import dev.ikna.data.repo.HourSlice
import dev.ikna.ui.text.S

/** Text equivalents of the existing chart data; no new goals or inferred scores. */
internal fun activityDescription(days: List<Boolean>, text: (String) -> String = S::t): String {
    if (days.isEmpty()) return text("stats.a11y.no_data")
    return text("stats.a11y.activity")
        .replace("{active}", days.count { it }.toString())
        .replace("{total}", days.size.toString())
}

internal fun hoursDescription(
    hours: List<HourSlice>,
    bestHour: Int?,
    text: (String) -> String = S::t
): String {
    if (hours.isEmpty()) return text("stats.a11y.no_data")
    // Use the repository's verdict; never promote an undersampled hour to "best".
    val slice = hours.lastOrNull { it.hour == bestHour && it.answers >= HOUR_CONFIDENT }
        ?: return text("stats.a11y.small_sample")
    val detail = text("stats.a11y.hour")
        .replace("{hour}", slice.hour.toString().padStart(2, '0') + ":00")
        .replace("{percent}", Math.round(slice.accuracy * 100).toString())
        .replace("{answers}", slice.answers.toString())
    return text("stats.a11y.hours").replace("{hours}", detail)
}

internal fun forecastDescription(values: List<Int>, text: (String) -> String = S::t): String =
    if (values.isEmpty()) text("stats.a11y.no_data") else text("stats.a11y.forecast_day")
        .replace("{days}", values.size.toString())
        .replace("{reviews}", values.joinToString(", "))

/** Mirrors the repository's floor for calling an hour measured rather than guessed. */
internal const val HOUR_CONFIDENT = 12
