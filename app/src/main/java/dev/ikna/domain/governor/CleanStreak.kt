package dev.ikna.domain.governor

/**
 * How many days in a row the user finished the plan, counted back from today.
 *
 * The load governor reads this and nothing else to decide whether the day's
 * ceiling may rise (`accelerateAfterCleanDays`), so it has to mean exactly what
 * it says. It used to be counted by walking the last thirty ROWS of
 * `daily_stats` and stopping at the first one whose plan was unfinished, which
 * is wrong twice over:
 *
 *  - A day with no session at all writes no row. The walk therefore stepped
 *    straight over an absence and joined two separate streaks into one, so a
 *    week off could still read as five clean days and buy a heavier plan on the
 *    evening the user came back.
 *  - The first row is today, and today's plan is unfinished until it is
 *    finished. Every streak therefore collapsed to zero each morning and only
 *    existed for the few hours between finishing a plan and midnight passing.
 *
 * So the count walks calendar days instead of rows: a missing day breaks the
 * streak, and today is allowed to be in progress without breaking anything.
 * Pure and dateless on purpose -- the day keys are produced by
 * [dev.ikna.domain.time.DayBoundary], which is the one place that knows when a
 * day starts.
 */
object CleanStreak {

    /**
     * @param days day keys from today backwards, most recent first.
     * @param planCompleted what `daily_stats.planCompleted` says for the days
     *   that have a row at all. A day missing from this map had no session.
     */
    fun count(days: List<String>, planCompleted: Map<String, Boolean>): Int {
        var streak = 0
        for ((index, day) in days.withIndex()) {
            val finished = planCompleted[day] == true
            if (finished) {
                streak++
                continue
            }
            // Today is allowed to be unfinished: it is still being worked on.
            // Any earlier gap ends the streak.
            if (index == 0) continue
            break
        }
        return streak
    }
}
