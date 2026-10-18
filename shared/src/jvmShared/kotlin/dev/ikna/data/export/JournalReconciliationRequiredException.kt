package dev.ikna.data.export

/** Derived stream/index proof is missing or inconsistent. Require explicit fresh profile consent. */
class JournalReconciliationRequiredException : IllegalStateException("Journal reconciliation required")
