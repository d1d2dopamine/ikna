package dev.ikna.sync

/** A successful control message cannot hide an unresolved, actionable page error.
 * In-memory UI state only; it grants no protocol authority and stores no payload. */
internal class JournalErrorLatch {
    data class Scope(val binding: HistoryBinding, val peerId: String, val operationId: String)
    private var previousScope: Scope? = null
    private var retained: String? = null

    @Synchronized fun update(scope: Scope?, caughtUp: Boolean, current: String?): String? {
        if (scope == null || caughtUp || previousScope != scope) retained = null
        previousScope = scope
        if (scope != null && !caughtUp && current in actionable) retained = current
        return retained
    }

    private companion object {
        val actionable = setOf("journal_item_too_large", "journal_reconciliation_required")
    }
}
