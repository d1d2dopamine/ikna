package dev.ikna.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSignalTrackerTest {
    private var clockMs = 1_000L
    private fun tracker() = ReviewSignalTracker { clockMs * 1_000_000L }

    @Test
    fun `latency ends at first drag not release`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 1_200L
        tracker.dragStarted()
        clockMs += 800L
        val result = tracker.snapshot(950f)
        assertEquals(1_200L, result.latencyMs!!)
        assertEquals(950f, result.swipeVelocityX!!, 0f)
        assertEquals(false, result.peeked)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `loading time is not reading time and layout does not reset it`() {
        val tracker = tracker()
        clockMs += 10_000L
        tracker.shown()
        clockMs += 700L
        tracker.shown()
        clockMs += 300L
        tracker.dragStarted()
        assertEquals(1_000L, tracker.snapshot(100f).latencyMs!!)
    }

    @Test
    fun `peek and cancelled attempts keep the first latency`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 900L
        tracker.dragStarted()
        tracker.reveal()
        // The first gesture springs back; the second commits the answer.
        clockMs += 500L
        tracker.dragStarted()
        val result = tracker.snapshot(-1_250f)
        assertEquals(900L, result.latencyMs!!)
        assertTrue(result.peeked!!)
        assertEquals(-1_250f, result.swipeVelocityX!!, 0f)
    }

    @Test
    fun `tap reveal is recorded too`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal()
        clockMs += 2_000L
        tracker.dragStarted()
        assertTrue(tracker.snapshot(0f).peeked!!)
    }

    @Test
    fun `zero velocity is measured not absent`() {
        val tracker = tracker()
        tracker.shown()
        tracker.dragStarted()
        val result = tracker.snapshot(0f)
        assertEquals(0f, result.swipeVelocityX!!, 0f)
        assertEquals(0L, result.latencyMs!!)
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
            clockMs += 100L
            tracker.dragStarted()
            tracker.interrupt(reason)
            tracker.shown()
            tracker.reveal()
            clockMs += 500L
            val result = tracker.snapshot(1_000f)
            assertEquals(reason, result.timingDiscardReason)
            assertEquals(100L, result.latencyMs!!)
            assertTrue(result.peeked!!)
        }
    }

    @Test
    fun `first interruption is the recorded reason`() {
        val tracker = tracker()
        tracker.shown()
        tracker.interrupt(TimingDiscardReason.FOCUS_LOST)
        tracker.interrupt(TimingDiscardReason.APP_BACKGROUND)
        tracker.dragStarted()
        assertEquals(TimingDiscardReason.FOCUS_LOST, tracker.snapshot(42f).timingDiscardReason)
    }

    @Test
    fun `more than sixty seconds is discarded without clamping the raw latency`() {
        val tracker = tracker()
        tracker.shown()
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
        clockMs += 60_000L
        tracker.dragStarted()
        assertNull(tracker.snapshot(0f).timingDiscardReason)
    }

    @Test
    fun `distraction after an early drag still discards timing`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 100L
        tracker.dragStarted()
        clockMs += 120_000L
        val result = tracker.snapshot(42f)
        assertEquals(100L, result.latencyMs!!)
        assertEquals(TimingDiscardReason.TIMEOUT, result.timingDiscardReason)
    }

    @Test
    fun `keyboard or accessibility cannot invent a swipe`() {
        val tracker = tracker()
        tracker.shown()
        tracker.reveal()
        val result = tracker.snapshot()
        assertNull(result.latencyMs)
        assertNull(result.swipeVelocityX)
        assertTrue(result.peeked!!)
        assertEquals(TimingDiscardReason.NO_SWIPE, result.timingDiscardReason)
    }

    @Test
    fun `a keyboard answer after a drag still has no swipe velocity`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 600L
        tracker.dragStarted()
        val result = tracker.snapshot()
        assertEquals(600L, result.latencyMs!!)
        assertEquals(TimingDiscardReason.NO_SWIPE, result.timingDiscardReason)
    }

    @Test
    fun `unknown and nonfinite observations never look usable`() {
        assertEquals(TimingDiscardReason.NOT_SHOWN, tracker().snapshot().timingDiscardReason)
        for (velocity in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val tracker = tracker()
            tracker.shown()
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
        tracker.dragStarted()
        assertEquals(TimingDiscardReason.INVALID_CLOCK, tracker.snapshot(10f).timingDiscardReason)
    }

    @Test
    fun `a new presentation has neither the old peek nor the old interruption`() {
        val old = tracker()
        old.shown()
        old.reveal()
        old.interrupt(TimingDiscardReason.FOCUS_LOST)
        val fresh = tracker()
        fresh.shown()
        fresh.dragStarted()
        val result = fresh.snapshot(0f)
        assertFalse(result.peeked!!)
        assertNull(result.timingDiscardReason)
    }

    @Test
    fun `an observation captured before animation stays immutable`() {
        val tracker = tracker()
        tracker.shown()
        clockMs += 300L
        tracker.dragStarted()
        val atRelease = tracker.snapshot(300f)
        clockMs += 90_000L
        tracker.interrupt(TimingDiscardReason.FOCUS_LOST)
        assertEquals(300L, atRelease.latencyMs!!)
        assertNull(atRelease.timingDiscardReason)
    }
}
