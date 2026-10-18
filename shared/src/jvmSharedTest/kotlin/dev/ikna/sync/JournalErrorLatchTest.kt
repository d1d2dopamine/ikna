package dev.ikna.sync

import org.junit.Assert.*
import org.junit.Test

class JournalErrorLatchTest {
    private val scope = JournalErrorLatch.Scope(HistoryBinding("test-group", "test-epoch"), "test-peer", "test-operation")

    @Test fun `control successes and transient errors preserve actionable failure until catchup`() {
        val latch = JournalErrorLatch()
        assertEquals("journal_item_too_large", latch.update(scope, false, "journal_item_too_large"))
        assertEquals("journal_item_too_large", latch.update(scope, false, null))
        assertEquals("journal_item_too_large", latch.update(scope, false, "connection_failed"))
        assertNull(latch.update(scope, true, null))
        assertNull(latch.update(scope, false, null))
    }

    @Test fun `operation peer generation and stopped lifecycle clear old actionable failure`() {
        for (replacement in listOf(null, scope.copy(operationId = "other-operation"),
            scope.copy(peerId = "other-peer"), scope.copy(binding = HistoryBinding("test-group", "other-epoch")))) {
            val latch = JournalErrorLatch()
            assertEquals("journal_reconciliation_required", latch.update(scope, false, "journal_reconciliation_required"))
            assertNull(latch.update(replacement, false, null))
        }
    }

    @Test fun `ordinary network errors never become a sticky journal error`() {
        val latch = JournalErrorLatch()
        assertNull(latch.update(scope, false, "connection_failed"))
        assertNull(latch.update(scope, false, null))
    }
}
