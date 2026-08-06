package dev.ikna.data.repo

/**
 * What the statistics screen shows beyond a single counter.
 *
 * Everything here is a measurement of the schedule or of the material, never a
 * score for the person. Retention says whether the intervals fit; the hours say
 * when answering is cheap; the minutes replace "how many cards" with the only
 * unit anyone actually budgets an evening in; the leeches point at phrases that
 * are not working. None of them can go down in a way that means "you failed",
 * and none of them reset.
 *
 * Nullable fields mean "not enough data yet" and must be rendered as an absence,
 * not as a zero. A retention of 0% computed from three answers is a lie with a
 * decimal point, and one invented number here would cost the screen the only
 * thing it has.
 */
data class StatsDigest(
    /** Share of reviews recalled, 0..1, or null until there are enough of them. */
    val retention: Double? = null,
    /** How many reviews that share was computed from. */
    val retentionSample: Int = 0,
    val minutesToday: Int = 0,
    val minutesLast7: Int = 0,
    /** Median seconds per answer, or null while the estimate would be a guess. */
    val medianSeconds: Int? = null,
    /** Hours of the day that have any answers at all, ascending. */
    val hours: List<HourSlice> = emptyList(),
    /** The hour with the best recall among those with enough answers behind them. */
    val bestHour: Int? = null,
    val leeches: List<LeechItem> = emptyList()
)

data class HourSlice(
    val hour: Int,
    val answers: Int,
    /** 0..1 within this hour. */
    val accuracy: Double
)

data class LeechItem(
    val text: String,
    val translation: String,
    val lapses: Int
)
