package dev.ikna.domain.session

/** Immutable accounting intent. Null is the historical ordinary review. */
object ReviewPurpose {
    const val OPTIONAL_HARD = "optional_hard"
    fun requireKnown(purpose: String?) {
        require(purpose == null || purpose == OPTIONAL_HARD) { "Unsupported review purpose" }
    }
}

/** A finite, process-local batch; only the issuing repository can use its token. */
data class OptionalHardBatch(
    val token: String,
    val day: String,
    val deckId: String?,
    val cards: List<SessionCard>
)
