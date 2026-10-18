package dev.ikna.domain.session

import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.HistoryBoundaryEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlin.test.*

class CommittedSessionChangesTest {
    private val plan = DailyPlanEntity("2026-10-17", "required:0,extra:0", 2, 2, 0, 0, "OK", 1, 123L)
    private fun observation() = SessionObservation(plan.day, 0L, 2L, 2L, plan, null, null, null)

    @Test fun `signal is nonblocking even while learning work is held`() = runBlocking {
        val work = Mutex(locked = true)
        val signal = CommittedSessionChanges()
        signal.notifyCommitted()
        signal.notifyCommitted()
        assertEquals(2L, signal.generation.value)
        assertTrue(work.isLocked)
        work.unlock()
    }

    @Test fun `already accepted action finishes but invalidation rejects stale presentation`() = runBlocking {
        val gate = SessionPresentationGate<String>()
        gate.remember(gate.generation, "before")
        val token = gate.generation
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        var committed = false; var published = false
        val answer = launch {
            entered.complete(Unit); release.await()
            committed = true
            published = gate.remember(token, "answer")
        }
        entered.await(); gate.invalidate(); release.complete(Unit); answer.join()
        assertTrue(committed); assertFalse(published)
        assertEquals("before", gate.observation)
    }

    @Test fun `unchanged resume preserves optional presentation token`() {
        val gate = SessionPresentationGate<SessionObservation>()
        val value = observation(); val token = gate.generation
        gate.remember(token, value)
        assertFalse(gate.changed(value.copy()))
        assertEquals(token, gate.generation)
        assertTrue(gate.changed(value.copy(reviewHighWater = 3, reviewCount = 3)))
    }

    @Test fun `refresh failure after its own invalidation still owns state but newer refresh wins`() {
        val gate = SessionPresentationGate<String>()
        val failureToken = gate.invalidate() // Resume observed changed history.
        assertTrue(gate.isCurrent(failureToken)) // Cleanup failure must hide old prompt.
        gate.invalidate()
        assertFalse(gate.isCurrent(failureToken)) // A genuinely newer refresh must not be cleared.
    }

    @Test fun `out of order refresh results cannot replace newest observation`() {
        val gate = SessionPresentationGate<String>()
        val old = gate.invalidate(); val fresh = gate.invalidate()
        assertTrue(gate.remember(fresh, "fresh"))
        assertFalse(gate.remember(old, "stale"))
        assertEquals("fresh", gate.observation)
    }

    @Test fun `own append and optional answer retain fresh stamp without cancel on next resume`() {
        val before = observation()
        val after = before.copy(reviewHighWater = 3, reviewCount = 3)
        assertTrue(SessionObservationPolicy.compatible(before, after, SessionPlanChange.Stable))
        val gate = SessionPresentationGate<SessionObservation>()
        gate.remember(0, before); gate.remember(0, after)
        assertFalse(gate.changed(after))
        val added = after.copy(storedPlan = plan.copy(plannedIds = plan.plannedIds + ",earned:0", plannedTotal = 3, extraRequested = 2))
        assertTrue(SessionObservationPolicy.compatible(before, added, SessionPlanChange.AppendExtras))
        assertFalse(SessionObservationPolicy.compatible(before, added, SessionPlanChange.Stable))
    }

    @Test fun `same timestamp replaced required plan fails even for own append`() {
        val before = observation()
        val replaced = before.copy(storedPlan = plan.copy(plannedIds = "different:0,extra:0"))
        assertFalse(SessionObservationPolicy.compatible(before, replaced, SessionPlanChange.AppendExtras))
        assertFalse(SessionObservationPolicy.compatible(before, replaced, SessionPlanChange.Stable))
    }

    @Test fun `own suppression removes only the reported target`() {
        val before = observation()
        val removed = before.copy(storedPlan = plan.copy(plannedIds = "extra:0", plannedTotal = 1))
        assertTrue(SessionObservationPolicy.compatible(before, removed, SessionPlanChange.RemoveTarget("required")))
        assertFalse(SessionObservationPolicy.compatible(before, removed, SessionPlanChange.RemoveTarget("another")))
        assertFalse(SessionObservationPolicy.compatible(before, removed, SessionPlanChange.AppendExtras))
    }

    @Test fun `merge between own commit and stamp read cannot adopt missing or changed boundary`() {
        val before = observation()
        assertFalse(SessionObservationPolicy.compatible(before, before.copy(storedPlan = null), SessionPlanChange.AppendExtras))
        assertFalse(SessionObservationPolicy.compatible(before, before.copy(day = "2026-10-18"), SessionPlanChange.Stable))
        assertFalse(SessionObservationPolicy.compatible(before,
            before.copy(boundary = HistoryBoundaryEntity(afterLocalId = 2, token = "reset")), SessionPlanChange.Stable))
    }
}
