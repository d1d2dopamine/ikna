package dev.ikna.domain.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Post-commit presentation notification only; never calls back into storage. */
class CommittedSessionChanges {
    private val mutableGeneration = MutableStateFlow(0L)
    val generation: StateFlow<Long> = mutableGeneration.asStateFlow()
    fun notifyCommitted() { mutableGeneration.update { it + 1L } }
}

/** Owned by the UI thread. A superseded read/write result cannot revive a stale prompt. */
class SessionPresentationGate<O> {
    var generation: Long = 0L
        private set
    var observation: O? = null
        private set

    fun invalidate(): Long { generation++; return generation }
    fun isCurrent(token: Long): Boolean = token == generation
    fun changed(value: O): Boolean = observation != null && observation != value
    fun remember(token: Long, value: O): Boolean {
        if (!isCurrent(token)) return false
        observation = value
        return true
    }
}

/** Expected local membership changes; this is presentation detection, not write authorization. */
sealed interface SessionPlanChange {
    data object Stable : SessionPlanChange
    data object AppendExtras : SessionPlanChange
    data class RemoveTarget(val chunkId: String) : SessionPlanChange
}

object SessionObservationPolicy {
    fun compatible(before: SessionObservation?, after: SessionObservation, change: SessionPlanChange): Boolean {
        val next = after.storedPlan ?: return false
        if (before == null) return true
        if (before.day != after.day || before.dayStart != after.dayStart || before.boundary != after.boundary ||
            before.configuredCutoff != after.configuredCutoff || before.configuredToken != after.configuredToken) return false
        val old = before.storedPlan ?: return false
        if (change == SessionPlanChange.Stable) return old == next
        // All non-membership decision fields must be identical, even when timestamps match.
        if (old.copy(plannedIds = next.plannedIds, plannedTotal = next.plannedTotal,
                extraRequested = next.extraRequested) != next) return false
        val oldRequired = (old.ids.size - old.extraRequested).coerceAtLeast(0)
        val nextRequired = (next.ids.size - next.extraRequested).coerceAtLeast(0)
        return when (change) {
            SessionPlanChange.Stable -> old == next
            SessionPlanChange.AppendExtras -> next.ids.take(old.ids.size) == old.ids &&
                next.ids.size >= old.ids.size && nextRequired == oldRequired &&
                next.plannedTotal - old.plannedTotal == next.ids.size - old.ids.size &&
                next.extraRequested - old.extraRequested == next.ids.size - old.ids.size
            is SessionPlanChange.RemoveTarget -> {
                val required = old.ids.take(oldRequired).filterNot { it.substringBeforeLast(':') == change.chunkId }
                val extra = old.ids.drop(oldRequired).filterNot { it.substringBeforeLast(':') == change.chunkId }
                next.ids == required + extra && nextRequired == required.size &&
                    next.plannedTotal == next.ids.size && next.extraRequested == extra.size
            }
        }
    }
}
