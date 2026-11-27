package dev.ikna.domain.session

/**
 * Immutable accounting intent for persisted review rows.
 * Null is the historical ordinary review; [OPTIONAL_HARD] is retained solely so
 * historical schema-14 rows, JSONL backups, and sync journals remain readable.
 */
object ReviewPurpose {
    const val OPTIONAL_HARD = "optional_hard"
    fun requireKnown(purpose: String?) {
        require(purpose == null || purpose == OPTIONAL_HARD) { "Unsupported review purpose" }
    }
}
