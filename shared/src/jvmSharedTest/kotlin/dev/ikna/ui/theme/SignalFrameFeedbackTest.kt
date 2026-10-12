package dev.ikna.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalFrameFeedbackTest {
    private fun feedback(
        placement: SignalFramePlacement = SignalFramePlacement.Inner,
        enabled: Boolean = true,
        hoverOwner: Boolean = false,
        focused: Boolean = false,
        pressed: Boolean = false,
        selected: Boolean = false,
        motionEnabled: Boolean = true,
        pressFeedback: Boolean = true
    ) = signalFrameFeedback(
        enabled, placement, hoverOwner, focused, pressed, selected, motionEnabled, pressFeedback
    )

    @Test
    fun touchPressAndReleaseNeverCreateAPerimeterOrStartTheClock() {
        for (placement in SignalFramePlacement.entries) {
            assertEquals(SignalFrameFeedback(false, false, false), feedback(placement))
            assertEquals(
                SignalFrameFeedback(false, false, true),
                feedback(placement, pressed = true)
            )
            // Release and cancellation both leave no lingering pressed state.
            assertEquals(SignalFrameFeedback(false, false, false), feedback(placement))
        }
    }

    @Test
    fun mouseHoverSurvivesPressAndResumesMotionOnRelease() {
        for (placement in SignalFramePlacement.entries) {
            assertEquals(
                SignalFrameFeedback(true, true, false),
                feedback(placement, hoverOwner = true)
            )
            assertEquals(
                SignalFrameFeedback(true, false, true),
                feedback(placement, hoverOwner = true, pressed = true)
            )
            assertTrue(feedback(placement, hoverOwner = true).frameMoving)
        }
    }

    @Test
    fun keyboardFocusIsVisibleAndStillInsideBorderlessControls() {
        assertEquals(SignalFrameFeedback(true, false, false), feedback(focused = true))
        assertEquals(
            SignalFrameFeedback(true, false, true),
            feedback(focused = true, pressed = true)
        )
        // Bordered controls retain their own focus boundary, never a root overlay.
        assertFalse(feedback(SignalFramePlacement.Outer, focused = true).frameVisible)
    }

    @Test
    fun selectedNavigationPersistsWithoutHoverAndRespectsReducedMotion() {
        assertEquals(SignalFrameFeedback(true, true, false), feedback(selected = true))
        assertEquals(
            SignalFrameFeedback(true, false, false),
            feedback(selected = true, motionEnabled = false)
        )
        assertFalse(feedback(SignalFramePlacement.Outer, selected = true).frameVisible)
    }

    @Test
    fun disablingAControlSuppressesStaleInteractionAndLocationState() {
        for (placement in SignalFramePlacement.entries) {
            assertEquals(
                SignalFrameFeedback(false, false, false),
                feedback(placement, enabled = false, hoverOwner = true, focused = true,
                    pressed = true, selected = true)
            )
        }
    }

    @Test
    fun hostsWithTheirOwnPressedFillDoNotReceiveADuplicateWash() {
        assertEquals(
            SignalFrameFeedback(false, false, false),
            feedback(pressed = true, pressFeedback = false)
        )
        assertEquals(
            SignalFrameFeedback(true, false, false),
            feedback(hoverOwner = true, pressed = true, pressFeedback = false)
        )
    }

    @Test
    fun reducedMotionKeepsTouchFeedbackAndStaticMouseAffordance() {
        assertEquals(
            SignalFrameFeedback(false, false, true),
            feedback(pressed = true, motionEnabled = false)
        )
        assertEquals(
            SignalFrameFeedback(true, false, false),
            feedback(hoverOwner = true, motionEnabled = false)
        )
    }
}
