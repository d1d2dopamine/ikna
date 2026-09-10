package dev.ikna.domain.session

import dev.ikna.domain.grading.INPUT_ACCESSIBILITY
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.grading.PEEK_REQUIRED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSignalTrackerTest {
    private var clockMs = 1_000L
    private fun tracker() = ReviewSignalTracker { clockMs * 1_000_000L }

    @Test
    fun `latency ends at final answer gesture after required reveal`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 700L
        tracker.reveal(INPUT_SWIPE)
        clockMs += 500L
        tracker.dragStarted()
        clockMs += 800L
        val result = tracker.snapshot(950f)
        assertEquals(1_200L, result.latencyMs!!)
        assertEquals(950f, result.swipeVelocityX!!, 0f)
        assertTrue(result.peeked!!)
        assertEquals(PEEK_REQUIRED, result.peekSemantics)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `loading time is not reading time and layout does not reset it`() {
        val tracker = tracker()
        clockMs += 10_000L
        tracker.shown()
        clockMs += 400L
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += 600L
        tracker.dragStarted()
        assertEquals(1_000L, tracker.snapshot(100f).latencyMs!!)
    }

    @Test
    fun `exploratory reveal pull is not mistaken for final answer`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 900L
        tracker.reveal(INPUT_SWIPE)
        // The reveal pull springs back; only the next deliberate throw answers.
        clockMs += 500L
        tracker.dragStarted()
        val result = tracker.snapshot(-1_250f)
        assertEquals(1_400L, result.latencyMs!!)
        assertTrue(result.peeked!!)
        assertEquals(-1_250f, result.swipeVelocityX!!, 0f)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `instant post reveal throw is never timing evidence`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += ReviewSignalTracker.MIN_REVEAL_CHECK_MS - 1L
        tracker.dragStarted()
        val result = tracker.snapshot(900f)
        assertEquals(TimingDiscardReason.REVEAL_NOT_VERIFIED, result.timingDiscardReason)
    }

    @Test
    fun `minimum verification boundary is accepted`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += ReviewSignalTracker.MIN_REVEAL_CHECK_MS
        tracker.dragStarted()
        val result = tracker.snapshot(0f)
        assertEquals(0f, result.swipeVelocityX!!, 0f)
        assertEquals(ReviewSignalTracker.MIN_REVEAL_CHECK_MS, result.latencyMs!!)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `interruptions remain sticky after returning and preserve raw observations`() {
        for (reason in listOf(
            TimingDiscardReason.FOCUS_LOST, TimingDiscardReason.APP_BACKGROUND,
            TimingDiscardReason.SCREEN_OFF, TimingDiscardReason.PRESENTATION_INTERRUPTED
        )) {
            val tracker = tracker()
            tracker.shown()
            tracker.reveal(INPUT_SWIPE)
            clockMs += 500L
            tracker.dragStarted()
            tracker.interrupt(reason)
            tracker.shown()
            val result = tracker.snapshot(1_000f)
            assertEquals(reason, result.timingDiscardReason)
            assertEquals(500L, result.latencyMs!!)
            assertTrue(result.peeked!!)
        }
    }

    @Test
    fun `first interruption is the recorded reason`() {
        val tracker = tracker()
        tracker.shown()
        tracker.interrupt(TimingDiscardReason.FOCUS_LOST)
        tracker.interrupt(TimingDiscardReason.APP_BACKGROUND)
        tracker.reveal(INPUT_SWIPE)
        clockMs += 300L
        tracker.dragStarted()
        assertEquals(TimingDiscardReason.FOCUS_LOST, tracker.snapshot(42f).timingDiscardReason)
    }

    @Test
    fun `more than sixty seconds is discarded without clamping raw latency`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += 60_001L
        tracker.dragStarted()
        val result = tracker.snapshot(100f)
        assertEquals(60_001L, result.latencyMs!!)
        assertEquals(TimingDiscardReason.TIMEOUT, result.timingDiscardReason)
    }

    @Test
    fun `exactly sixty seconds is within the ceiling`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 100L
        tracker.reveal(INPUT_SWIPE)
        clockMs += 59_900L
        tracker.dragStarted()
        assertNull(tracker.snapshot(0f).timingDiscardReason)
    }

    @Test
    fun `distraction after answer gesture still discards timing`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += 300L
        tracker.dragStarted()
        clockMs += 120_000L
        val result = tracker.snapshot(42f)
        assertEquals(300L, result.latencyMs!!)
        assertEquals(TimingDiscardReason.TIMEOUT, result.timingDiscardReason)
    }

    @Test
    fun `keyboard latency ends at final answer key not reveal key`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 700L
        tracker.reveal(INPUT_KEYBOARD)
        clockMs += 500L
        tracker.keyboardStarted()
        val result = tracker.snapshot(inputMethod = INPUT_KEYBOARD)
        assertEquals(1_200L, result.latencyMs!!)
        assertNull(result.swipeVelocityX)
        assertTrue(result.peeked!!)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `accessibility cannot enter native timing calibration`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_ACCESSIBILITY)
        clockMs += 300L
        tracker.answerStarted(INPUT_ACCESSIBILITY)
        val result = tracker.snapshot(inputMethod = INPUT_ACCESSIBILITY)
        assertEquals(300L, result.latencyMs!!)
        assertNull(result.swipeVelocityX)
        assertTrue(result.peeked!!)
        assertEquals(TimingDiscardReason.NO_SWIPE, result.timingDiscardReason)
    }

    @Test
    fun `switching modality between reveal and answer discards timing`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += 600L
        tracker.keyboardStarted()
        val result = tracker.snapshot(inputMethod = INPUT_KEYBOARD)
        assertEquals(600L, result.latencyMs!!)
        assertEquals(TimingDiscardReason.MIXED_INPUT, result.timingDiscardReason)
    }

    @Test
    fun `answer without the required reveal is discarded`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 600L
        tracker.dragStarted()
        val result = tracker.snapshot(500f)
        assertFalse(result.peeked!!)
        assertEquals(TimingDiscardReason.ANSWER_NOT_REVEALED, result.timingDiscardReason)
    }

    @Test
    fun `unknown and nonfinite observations never look usable`() {
        assertEquals(TimingDiscardReason.NOT_SHOWN, tracker().snapshot().timingDiscardReason)
        for (velocity in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val tracker = tracker()
            tracker.shown()
            tracker.reveal(INPUT_SWIPE)
            clockMs += 300L
            tracker.dragStarted()
            val result = tracker.snapshot(velocity)
            assertNull(result.swipeVelocityX)
            assertEquals(TimingDiscardReason.NO_SWIPE, result.timingDiscardReason)
        }
    }

    @Test
    fun `backward clock is not coerced into a fast answer`() {
        val tracker = tracker()
        tracker.shown()
        clockMs -= 1L
        tracker.reveal(INPUT_SWIPE)
        tracker.dragStarted()
        assertEquals(TimingDiscardReason.INVALID_CLOCK, tracker.snapshot(10f).timingDiscardReason)
    }

    @Test
    fun `a new presentation has neither old reveal nor interruption`() {
        val old = tracker()
        old.shown()
        old.reveal(INPUT_SWIPE)
        old.interrupt(TimingDiscardReason.FOCUS_LOST)
        val fresh = tracker()
        fresh.shown()
        clockMs += 300L
        fresh.dragStarted()
        val result = fresh.snapshot(0f)
        assertFalse(result.peeked!!)
        assertEquals(TimingDiscardReason.ANSWER_NOT_REVEALED, result.timingDiscardReason)
    }

    @Test
    fun `observation captured before animation stays immutable`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal(INPUT_SWIPE)
        clockMs += 300L
        tracker.dragStarted()
        val atRelease = tracker.snapshot(300f)
        clockMs += 90_000L
        tracker.interrupt(TimingDiscardReason.FOCUS_LOST)
        assertEquals(300L, atRelease.latencyMs!!)
        assertNull(atRelease.timingDiscardReason)
    }
}
