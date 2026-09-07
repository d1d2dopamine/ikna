package dev.ikna.domain.optimizer
import dev.ikna.domain.fsrs.Verdict

/** Product policy, not another preference the learner must understand. */
object AutomaticLearningPolicy {
    // DerivedGrading still owns warmup, per-level evidence, noise and reveal guards.
    const val DERIVED_WHEN_READY = true
    const val ELIGIBILITY_RECHECK_MS = 86_400_000L
    fun nextFitAt(latest: FitAttempt?): Long = when {
        latest == null -> 0L
        latest.verdict == Verdict.TOO_FEW_ANSWERS -> latest.attemptedAt + ELIGIBILITY_RECHECK_MS
        else -> latest.nextFitAt
    }
}
