package dev.ikna.domain.grading

import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.session.ReviewSignals
import kotlin.math.sqrt

const val DERIVED_GRADING_VERSION = 1
const val GRADING_WINDOW_SIZE = 200
const val GRADING_WARMUP = 50
const val GRADING_LEVEL_WARMUP = 20
const val INPUT_SWIPE = "swipe"
const val INPUT_KEYBOARD = "keyboard"
const val INPUT_ACCESSIBILITY = "accessibility"
const val PEEK_REQUIRED = "required_reveal"
const val PEEK_OPTIONAL = "optional_peek"

data class TimingSample(val latencyMs: Long, val level: Int, val promptLength: Int) {
    val valid: Boolean get() = latencyMs in 1L..60_000L && level in 0..2 && promptLength in 1..4_000
    // Level is stratified, not assigned a population multiplier. Square-root
    // length scaling is a conservative, versioned reading-cost heuristic.
    val normalized: Double get() = latencyMs.toDouble() / sqrt(promptLength.toDouble())
}

data class TimingThresholds(val lower: Double, val median: Double, val upper: Double)

/** A bounded ring, rebuilt from the latest valid undo-aware on-device rows. */
class TimingWindow {
    private val entries = arrayOfNulls<TimingSample>(GRADING_WINDOW_SIZE)
    private var next = 0
    var size: Int = 0
        private set

    fun add(sample: TimingSample) {
        if (!sample.valid) return
        entries[next] = sample
        next = (next + 1) % entries.size
        size = (size + 1).coerceAtMost(entries.size)
    }

    fun samples(): List<TimingSample> = (0 until size).map { offset ->
        entries[(next - size + offset + entries.size) % entries.size]!!
    }

    fun thresholds(level: Int): TimingThresholds? {
        if (size < GRADING_WARMUP) return null
        val values = samples().filter { it.level == level }.map { it.normalized }.sorted()
        if (values.size < GRADING_LEVEL_WARMUP) return null
        return TimingThresholds(percentile(values, 0.25), percentile(values, 0.5), percentile(values, 0.75))
    }

    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        val position = (sorted.size - 1) * fraction
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }
}

data class GradeDecision(
    val rating: Rating,
    val reason: String,
    val timingDiscardReason: String?,
    val acceptedSample: TimingSample?
)

/**
 * Read the PRIOR window, decide, then append the accepted sample. Never train on
 * the current answer before classifying it. Velocity is logged, not a predictor.
 * Unknowns, ties, cold starts and ambiguous peeks cannot produce EASY.
 */
object DerivedGrading {
    fun decide(
        input: Rating,
        signals: ReviewSignals,
        level: Int,
        promptLength: Int?,
        window: TimingWindow,
        enabled: Boolean
    ): GradeDecision {
        val sample = if (signals.latencyMs != null && promptLength != null) {
            TimingSample(signals.latencyMs, level, promptLength)
        } else null
        val thresholds = window.thresholds(level)
        val discarded = signals.timingDiscardReason ?: when {
            signals.inputMethod != INPUT_SWIPE -> "no_swipe"
            signals.swipeVelocityX?.isFinite() != true -> "no_swipe"
            sample?.valid != true -> "invalid_timing_context"
            thresholds != null && sample.normalized > thresholds.median * 5.0 -> "above_personal_ceiling"
            else -> null
        }
        val accepted = sample?.takeIf { discarded == null && it.valid }
        fun result(rating: Rating, reason: String) = GradeDecision(rating, reason, discarded, accepted)

        // Manual HARD/EASY are never reinterpreted or capped. Left is AGAIN.
        if (input != Rating.GOOD) return result(input, "explicit_input")
        if (!enabled) return result(input, "disabled")
        if (accepted == null) return result(input, "unknown_timing")
        if (thresholds == null) return result(input, "warmup")
        if (signals.peekSemantics !in setOf(PEEK_OPTIONAL, PEEK_REQUIRED)) {
            return result(input, "unknown_peek_semantics")
        }
        if (signals.peeked == null) return result(input, "unknown_peek")
        if (signals.peekSemantics == PEEK_OPTIONAL && signals.peeked) {
            return result(Rating.HARD, "peeked")
        }
        // An indistinguishable window provides no evidence, even at its edges.
        if (thresholds.upper <= thresholds.lower) return result(input, "flat_window")
        if (accepted.normalized > thresholds.upper) return result(Rating.HARD, "slow")
        if (accepted.normalized < thresholds.lower) {
            // The current native UI requires a reveal. Do not call that an
            // optional peek, or call it confidently absent: both would lie.
            if (signals.peekSemantics == PEEK_OPTIONAL && !signals.peeked) {
                return result(Rating.EASY, "fast_no_peek")
            }
            return result(input, "required_reveal_ambiguous")
        }
        return result(input, "ordinary")
    }
}
