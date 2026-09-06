package dev.ikna.domain.optimizer
import dev.ikna.domain.fsrs.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val OPTIMIZER_REFIT_MS = 30L * 86_400_000L
@Serializable
data class FitAttempt(
    val formatVersion: Int = 1, val estimatorVersion: Int = 1,
    val attemptedAt: Long, val sourceFingerprint: String, val sourceAnswers: Int,
    val firstReviewAt: Long?, val lastReviewAt: Long?, val scoredAnswers: Int,
    val verdict: Verdict, val heldOutLossDefaults: Double?, val heldOutLossOptimised: Double?,
    val parameters: FsrsSnapshot?
) {
    fun isValid(): Boolean {
        if (formatVersion != 1 || estimatorVersion != 1 || attemptedAt <= 0 ||
            attemptedAt > Long.MAX_VALUE - OPTIMIZER_REFIT_MS ||
            sourceAnswers !in 0..FsrsOptimizer.MAX_ANSWERS || scoredAnswers !in 0..sourceAnswers ||
            !sourceFingerprint.matches(Regex("[0-9a-f]{64}"))) return false
        val base = heldOutLossDefaults; val fit = heldOutLossOptimised
        if (verdict == Verdict.TOO_FEW_ANSWERS) return parameters == null &&
            scoredAnswers < FsrsOptimizer.MIN_SCORED_ANSWERS && base == null && fit == null
        if (scoredAnswers < FsrsOptimizer.MIN_SCORED_ANSWERS || base == null || fit == null ||
            !base.isFinite() || !fit.isFinite() || base < 0 || fit < 0) return false
        return when (verdict) {
            Verdict.ACCEPTED -> parameters?.isValid() == true && fit < base - FsrsOptimizer.MIN_IMPROVEMENT
            Verdict.NO_IMPROVEMENT -> parameters == null && fit >= base - FsrsOptimizer.MIN_IMPROVEMENT
            Verdict.TOO_FEW_ANSWERS -> false
        }
    }
    val accepted: Boolean get() = verdict == Verdict.ACCEPTED && isValid()
    val nextFitAt: Long get() = if (verdict == Verdict.TOO_FEW_ANSWERS) 0L else attemptedAt + OPTIMIZER_REFIT_MS
}
@Serializable
data class StoredOptimizer(val formatVersion: Int = 1, val enabled: Boolean = false,
    val latest: FitAttempt? = null, val candidate: FitAttempt? = null, val applied: FitAttempt? = null) {
    fun isValid(): Boolean = formatVersion == 1 && (latest == null || latest.isValid()) &&
        (candidate == null || candidate.accepted) && (applied == null || applied.accepted) &&
        (!enabled || applied != null)
}
object OptimizerStateCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun decode(raw: String?): StoredOptimizer? = if (raw == null) StoredOptimizer() else runCatching {
        require(raw.length <= 65536)
        json.decodeFromString(StoredOptimizer.serializer(), raw).also { require(it.isValid()) }
    }.getOrNull()
    fun encode(state: StoredOptimizer): String {
        require(state.isValid()); return json.encodeToString(StoredOptimizer.serializer(), state)
    }
}
enum class OptimizerIssue { STORAGE, FAILED, STALE_HISTORY, CANCELLED, COOLDOWN }
data class OptimizerUiState(val ready: Boolean = false, val running: Boolean = false,
    val usingOptimized: Boolean = false, val stored: StoredOptimizer = StoredOptimizer(),
    val issue: OptimizerIssue? = null)
