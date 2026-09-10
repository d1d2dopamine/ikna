package dev.ikna.domain.session

import dev.ikna.domain.grading.INPUT_ACCESSIBILITY
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.grading.PEEK_REQUIRED

/**
 * Observations, never a grade. Null means unknown, not zero/false.
 *
 * V2 latency ends when the final answer action begins, after the mandatory
 * reveal. A discarded latency stays in the append-only log for diagnosis, but
 * must not enter a future calibration window. See docs/GRADING.md.
 */
data class ReviewSignals(
    val latencyMs: Long? = null,
    /** Signed horizontal release velocity, in Compose pixels per second. */
    val swipeVelocityX: Float? = null,
    /** True when the mandatory back was shown before the answer action. */
    val peeked: Boolean? = null,
    val timingDiscardReason: String? = null,
    val inputMethod: String? = null,
    val peekSemantics: String? = null
)

/** Stable strings in the export format. Unknown future reasons must stay readable. */
object TimingDiscardReason {
    const val NOT_SHOWN = "not_shown"
    const val NO_SWIPE = "no_swipe"
    const val FOCUS_LOST = "focus_lost"
    const val APP_BACKGROUND = "app_background"
    const val SCREEN_OFF = "screen_off"
    const val PRESENTATION_INTERRUPTED = "presentation_interrupted"
    const val TIMEOUT = "timeout"
    const val INVALID_CLOCK = "invalid_clock"
    const val MIXED_INPUT = "mixed_input"
    const val ANSWER_NOT_REVEALED = "answer_not_revealed"
    const val REVEAL_NOT_VERIFIED = "reveal_not_verified"
}

/**
 * One presentation's stopwatch, confined to the UI thread on both platforms.
 * Starts at first layout and ends when the final answer gesture/key begins. The
 * reveal and answer must use one modality; mouse and keyboard populations never
 * contaminate each other. A required answer must stay visible briefly enough to
 * be checked before timing can influence scheduling.
 *
 * Uses a monotonic clock, independent of wall-clock corrections. Android sleep
 * is also rejected by lifecycle/screen-off observers. Losing focus is sticky:
 * returning to the card cannot make the interrupted attempt look fast.
 */
class ReviewSignalTracker(
    private val nowNanos: () -> Long = System::nanoTime
) {
    private var shownAt: Long? = null
    private var answerInputMs: Long? = null
    private var answerInputMethod: String? = null
    private var revealedAtMs: Long? = null
    private var revealInputMethod: String? = null
    private var peeked = false
    private var discarded: String? = null

    fun shown() {
        if (shownAt == null) shownAt = nowNanos()
    }

    /** The back became visible through a real pointer, key, or accessibility action. */
    fun reveal(inputMethod: String) {
        peeked = true
        if (revealedAtMs == null) revealedAtMs = elapsedMs()
        if (revealInputMethod == null) revealInputMethod = inputMethod
    }

    fun interrupt(reason: String) {
        if (discarded == null) discarded = reason
    }

    /** Final pointer answer gesture, never the exploratory pull that reveals. */
    fun dragStarted() {
        answerStarted(INPUT_SWIPE)
    }

    /** Final keyboard answer action, never the key that merely reveals. */
    fun keyboardStarted() {
        answerStarted(INPUT_KEYBOARD)
    }

    fun answerStarted(inputMethod: String) {
        if (answerInputMs == null) {
            answerInputMs = elapsedMs()
            answerInputMethod = inputMethod
            if (answerInputMs == null) interrupt(TimingDiscardReason.NOT_SHOWN)
        }
    }

    /** Call at release, BEFORE the throw animation or repository work starts. */
    fun snapshot(
        swipeVelocityX: Float? = null,
        inputMethod: String = if (swipeVelocityX != null) INPUT_SWIPE else INPUT_ACCESSIBILITY
    ): ReviewSignals {
        val elapsed = elapsedMs()
        val velocity = swipeVelocityX?.takeIf { it.isFinite() }
        val verificationMs = if (answerInputMs != null && revealedAtMs != null) {
            answerInputMs!! - revealedAtMs!!
        } else null
        val reason = discarded ?: when {
            elapsed == null -> TimingDiscardReason.NOT_SHOWN
            elapsed > MAX_TIMING_MS -> TimingDiscardReason.TIMEOUT
            answerInputMs == null -> TimingDiscardReason.NO_SWIPE
            answerInputMethod != inputMethod -> TimingDiscardReason.MIXED_INPUT
            !peeked || revealedAtMs == null || revealInputMethod == null -> TimingDiscardReason.ANSWER_NOT_REVEALED
            revealInputMethod != inputMethod -> TimingDiscardReason.MIXED_INPUT
            verificationMs == null || verificationMs < MIN_REVEAL_CHECK_MS -> TimingDiscardReason.REVEAL_NOT_VERIFIED
            inputMethod == INPUT_SWIPE && velocity == null -> TimingDiscardReason.NO_SWIPE
            inputMethod == INPUT_KEYBOARD -> null
            inputMethod != INPUT_SWIPE -> TimingDiscardReason.NO_SWIPE
            else -> null
        }
        return ReviewSignals(
            latencyMs = answerInputMs,
            swipeVelocityX = velocity,
            peeked = peeked,
            timingDiscardReason = reason,
            inputMethod = inputMethod,
            peekSemantics = PEEK_REQUIRED
        )
    }

    private fun elapsedMs(): Long? {
        val start = shownAt ?: return null
        val elapsed = nowNanos() - start
        if (elapsed < 0L) {
            interrupt(TimingDiscardReason.INVALID_CLOCK)
            return null
        }
        return elapsed / 1_000_000L
    }

    companion object {
        const val MAX_TIMING_MS = 60_000L
        /** Reject an instant post-reveal throw as unverified, never as EASY. */
        const val MIN_REVEAL_CHECK_MS = 250L
    }
}
