package dev.ikna.ui.session

import dev.ikna.domain.governor.GovernorReason
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Selects a stable local message from the current review context. */
fun sessionFinishMessageKey(state: SessionUiState, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val local = now.atZone(zone)
    val next = state.nextDueAt?.let(Instant::ofEpochMilli)
    val soon = next != null && next.isAfter(now) && Duration.between(now, next) <= Duration.ofHours(3)
    val category = when {
        state.reason == GovernorReason.PACK_EXHAUSTED -> "introduced"
        soon -> "soon"
        local.hour >= 22 || local.hour < 5 -> "night"
        state.reason in setOf(GovernorReason.RETURN_MODE, GovernorReason.POST_SKIP_WARMUP, GovernorReason.LOW_ACTIVITY) ->
            "return"
        state.answeredToday > 0 -> "done"
        else -> "waiting"
    }
    val seed = local.toLocalDate().toEpochDay() + local.hour / 4 + state.deckTitle.orEmpty().hashCode()
    return "finish.$category.${Math.floorMod(seed, 4L) + 1}"
}
