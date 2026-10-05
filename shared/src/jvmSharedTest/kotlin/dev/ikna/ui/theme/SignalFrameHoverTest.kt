package dev.ikna.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class SignalFrameHoverTest {
    private val row = Any()
    private val small = Any()
    private val button = Any()

    private fun state() = SignalFrameHoverState().apply {
        setArea(row, 20000f)
        setBounds(row, SignalFrameHoverBounds(0f, 0f, 200f, 100f))
        setArea(small, 100f)
        setBounds(small, SignalFrameHoverBounds(0f, 0f, 10f, 10f))
        setArea(button, 2000f)
        setBounds(button, SignalFrameHoverBounds(220f, 0f, 320f, 20f))
    }

    @Test
    fun smallestNestedTargetOwnsHoverThenReturnsItToTheRow() {
        val state = state()
        state.move(row, 5f, 5f)
        state.move(small, 5f, 5f)
        assertSame(small, state.owner)
        state.exit(small)
        state.move(row, 20f, 5f)
        assertSame(row, state.owner)
    }

    @Test
    fun lostExitFromSmallTargetCannotSuppressAnUnrelatedButton() {
        val state = state()
        state.move(small, 5f, 5f)
        assertSame(small, state.owner)
        // Reproduce the old arbiter failure: deliberately omit the old Exit.
        // The previous size-only map selected small forever, except on press.
        state.move(button, 250f, 5f)
        assertSame(button, state.owner)
        state.move(row, 50f, 50f)
        assertSame(row, state.owner)
    }

    @Test
    fun moveRestoresHoverAfterInputCancellationWithoutANewEnter() {
        val state = state()
        state.move(button, 250f, 5f)
        state.exit(button)
        assertNull(state.owner)
        state.move(button, 251f, 5f)
        assertSame(button, state.owner)
    }

    @Test
    fun detachAndReattachDoNotLeaveAnInvisibleHoverOwner() {
        val state = state()
        repeat(3) {
            state.move(small, 5f, 5f)
            assertSame(small, state.owner)
            state.detach(small)
            assertNull(state.owner)
            state.setBounds(small, SignalFrameHoverBounds(0f, 0f, 10f, 10f))
            // Repositioning/repainting alone does not invent hover.
            assertNull(state.owner)
        }
        state.move(button, 250f, 5f)
        assertSame(button, state.owner)
    }

    @Test
    fun scrollingAHoveredTargetOutOfItsClippedBoundsRevokesOwnership() {
        val state = state()
        state.move(small, 5f, 5f)
        state.move(row, 5f, 5f)
        assertSame(small, state.owner)
        state.setBounds(small, SignalFrameHoverBounds(0f, 0f, 0f, 0f))
        assertSame(row, state.owner)
        state.setBounds(row, SignalFrameHoverBounds(0f, 20f, 200f, 100f))
        assertNull(state.owner)
    }

    @Test
    fun removingTheNestedTargetRestoresTheParentAndLastExitClearsHover() {
        val state = state()
        state.move(row, 5f, 5f)
        state.move(small, 5f, 5f)
        state.remove(small)
        assertSame(row, state.owner)
        state.exit(row)
        assertNull(state.owner)
    }

    @Test
    fun equalAreaTieUsesTheLatestEntryAndEmptyBoundsNeverOwnHover() {
        val state = state()
        state.setBounds(button, SignalFrameHoverBounds(0f, 0f, 10f, 10f))
        state.setArea(button, 100f)
        state.move(small, 5f, 5f)
        state.move(button, 5f, 5f)
        assertSame(button, state.owner)
        state.setBounds(button, SignalFrameHoverBounds(0f, 0f, 0f, 0f))
        assertSame(small, state.owner)
    }

    @Test
    fun changingDrawingPlacementKeepsInputOwnershipUntilTheFrameIsDisposed() {
        val coordinator = SignalFrameCoordinator()
        coordinator.setArea(button, 2000f)
        coordinator.setHoverBounds(button, Rect(220f, 0f, 320f, 20f))
        coordinator.movePointer(button, Offset(250f, 5f))
        coordinator.registerOuter(button, SignalFrameOverlayEntry())
        coordinator.unregisterOuter(button)
        assertSame(button, coordinator.activeHoverOwner)
        coordinator.unregister(button)
        assertNull(coordinator.activeHoverOwner)
    }
}
