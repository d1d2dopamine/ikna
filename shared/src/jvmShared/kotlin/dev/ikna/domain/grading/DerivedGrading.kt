package dev.ikna.domain.grading

import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.session.ReviewSignals
import kotlin.math.sqrt

/** Version of the bounded HARD/EASY schedule transform stored in review rows. */
const val DERIVED_GRADING_VERSION = 1
const val GRADING_WINDOW_SIZE = 200
const val GRADING_WARMUP = 50
const val GRADING_LEVEL_WARMUP = 20
const val EASY_MIN_PRIOR_SUCCESSES = 3
const val INPUT_SWIPE = "swipe"
const val INPUT_KEYBOARD = "keyboard"
const val INPUT_ACCESSIBILITY = "accessibility"

/**
 * Current timing protocol. V2 measures until the final answer action after the
 * mandatory reveal; changing this string starts a clean calibration cohort.
 */
const val PEEK_REQUIRED = "required_reveal_verified_v2"

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
        // EASY is deliberately rarer than HARD: fastest 10%, slowest 25%.
        return TimingThresholds(percentile(values, 0.10), percentile(values, 0.5), percentile(values, 0.75))
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
 * the current answer before classifying it. Velocity is logged, not a predictor;
 * it only proves that a pointer observation is genuinely a swipe. Keyboard timing
 * has its own calibration window and never borrows or contaminates that window.
 *
 * The UI always reveals the answer. V2 therefore measures the complete verified
 * response, not a fictional no-peek state: fast mature recall may become EASY,
 * ordinary recall stays GOOD, and clear slow recall may become HARD.
 */
object DerivedGrading {
    fun decide(
        input: Rating,
        signals: ReviewSignals,
        level: Int,
        promptLength: Int?,
        window: TimingWindow,
        enabled: Boolean,
        easyEligible: Boolean
    ): GradeDecision {
        val sample = if (signals.latencyMs != null && promptLength != null) {
            TimingSample(signals.latencyMs, level, promptLength)
        } else null
        val thresholds = window.thresholds(level)
        val supportedInput = when (signals.inputMethod) {
            INPUT_SWIPE -> signals.swipeVelocityX?.isFinite() == true
            INPUT_KEYBOARD -> signals.swipeVelocityX == null
            else -> false
        }
        val discarded = signals.timingDiscardReason ?: when {
            !supportedInput -> "unsupported_input"
            signals.peekSemantics != PEEK_REQUIRED -> "unsupported_reveal_protocol"
            signals.peeked != true -> "answer_not_revealed"
            sample?.valid != true -> "invalid_timing_context"
            thresholds != null && sample.normalized > thresholds.median * 5.0 -> "above_personal_ceiling"
            else -> null
        }
        // Calibration is built from verified known answers only. AGAIN remains
        // an outcome for FSRS/governor, but does not define fluent-answer speed.
        val accepted = sample?.takeIf { input == Rating.GOOD && discarded == null && it.valid }
        fun result(rating: Rating, reason: String) = GradeDecision(rating, reason, discarded, accepted)

        // Manual legacy HARD/EASY are never reinterpreted. Left remains AGAIN.
        if (input != Rating.GOOD) return result(input, "explicit_input")
        if (!enabled) return result(input, "disabled")
        if (accepted == null) return result(input, "unknown_timing")
        if (thresholds == null) return result(input, "warmup")
        // An indistinguishable window provides no evidence, even at its edges.
        if (thresholds.upper <= thresholds.lower) return result(input, "flat_window")
        if (accepted.normalized > thresholds.upper) return result(Rating.HARD, "slow_verified")
        if (accepted.normalized < thresholds.lower) {
            if (!easyEligible) return result(input, "fast_not_mature")
            return result(Rating.EASY, "fast_verified")
        }
        return result(input, "ordinary_verified")
    }
}
