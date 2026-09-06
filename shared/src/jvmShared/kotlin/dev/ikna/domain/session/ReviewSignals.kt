package dev.ikna.domain.session

/**
 * Observations, never a grade. Null means unknown, not zero/false.
 *
 * A discarded latency stays in the append-only log for diagnosis, but must not
 * enter a future calibration window. Only latencyMs != null with a null
 * timingDiscardReason is a usable timing. See docs/GRADING.md.
 */
data class ReviewSignals(
    val latencyMs: Long? = null,
    /** Signed horizontal release velocity, in Compose pixels per second. */
    val swipeVelocityX: Float? = null,
    /** The back was consulted, by any reveal path (including tap/keyboard). */
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
}

/**
 * One presentation's stopwatch, confined to the UI thread on both platforms.
 * Starts at the first layout of the card, not while loading the queue. The first
 * drag includes a reveal drag; failed/cancelled pulls do not reset the clock.
 *
 * Uses a monotonic clock, independent of wall-clock corrections. Android sleep
 * is also rejected by lifecycle/screen-off observers. Losing focus is sticky:
 * returning to the card cannot make the interrupted attempt look fast.
 */
class ReviewSignalTracker(
    private val nowNanos: () -> Long = System::nanoTime
) {
    private var shownAt: Long? = null
    private var firstDragMs: Long? = null
    private var peeked = false
    private var discarded: String? = null

    fun shown() {
        if (shownAt == null) shownAt = nowNanos()
    }

    fun reveal() {
        peeked = true
    }

    fun interrupt(reason: String) {
        if (discarded == null) discarded = reason
    }

    fun dragStarted() {
        if (firstDragMs == null) {
            firstDragMs = elapsedMs()
            if (firstDragMs == null) interrupt(TimingDiscardReason.NOT_SHOWN)
        }
    }

    /** Call at release, BEFORE the throw animation or repository work starts. */
    fun snapshot(
        swipeVelocityX: Float? = null,
        inputMethod: String = if (swipeVelocityX != null) "swipe" else "accessibility"
    ): ReviewSignals {
        val elapsed = elapsedMs()
        val velocity = swipeVelocityX?.takeIf { it.isFinite() }
        val reason = discarded ?: when {
            elapsed == null -> TimingDiscardReason.NOT_SHOWN
            elapsed > MAX_TIMING_MS -> TimingDiscardReason.TIMEOUT
            velocity == null || firstDragMs == null -> TimingDiscardReason.NO_SWIPE
            else -> null
        }
        return ReviewSignals(
            latencyMs = firstDragMs,
            swipeVelocityX = velocity,
            peeked = peeked,
            timingDiscardReason = reason,
            inputMethod = inputMethod,
            peekSemantics = "required_reveal"
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
        // The absolute-ceiling alternative specified in GRADING.md. Adaptive
        // percentiles and the per-person window belong to the later experiment.
        const val MAX_TIMING_MS = 60_000L
    }
}
