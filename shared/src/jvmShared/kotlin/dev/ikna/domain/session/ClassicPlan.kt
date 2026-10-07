package dev.ikna.domain.session

import dev.ikna.data.db.DailyPlanEntity

/** Retire old presentation keys without rebuilding the day's allowance or learner history. */
fun DailyPlanEntity.classicOnly(): DailyPlanEntity {
    val original = ids
    if (original.all { it.substringAfterLast(':') == "0" }) return this
    val split = (original.size - extraRequested).coerceIn(0, original.size)
    val required = original.take(split).filter { it.substringAfterLast(':') == "0" }
    val extras = original.drop(split).filter { it.substringAfterLast(':') == "0" }
    val active = required + extras
    return copy(plannedIds = active.joinToString(","), plannedTotal = active.size, extraRequested = extras.size)
}
