package dev.ikna.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** Direction is the meaning of Shared Axis X, so pin it without a device. */
class SharedAxisTransitionTest {

    @Test
    fun forward_navigation_enters_from_the_right_and_leaves_to_the_left() {
        assertEquals(14, sharedAxisEnterOffset(forward = true, travelPx = 14))
        assertEquals(-14, sharedAxisExitOffset(forward = true, travelPx = 14))
    }

    @Test
    fun back_navigation_mirrors_the_axis() {
        assertEquals(-14, sharedAxisEnterOffset(forward = false, travelPx = 14))
        assertEquals(14, sharedAxisExitOffset(forward = false, travelPx = 14))
    }

    @Test
    fun zero_travel_never_invents_a_direction() {
        assertEquals(0, sharedAxisEnterOffset(forward = true, travelPx = 0))
        assertEquals(0, sharedAxisExitOffset(forward = false, travelPx = 0))
    }
}
