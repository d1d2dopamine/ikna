package dev.ikna.ui.theme

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class AnchoredActionPositionTest {
    private val position = AnchoredActionPosition(gap = 6, edge = 8)
    private val window = IntSize(400, 600)
    private val panel = IntSize(240, 200)

    @Test
    fun opensBelowWhenThereIsRoom() {
        assertEquals(
            IntOffset(24, 94),
            position.calculatePosition(IntRect(24, 40, 170, 88), window, LayoutDirection.Ltr, panel)
        )
    }

    @Test
    fun switchesAboveNearTheBottomAndStaysInsideTheRightEdge() {
        assertEquals(
            IntOffset(152, 304),
            position.calculatePosition(IntRect(250, 510, 396, 558), window, LayoutDirection.Ltr, panel)
        )
    }

    @Test
    fun clampsInsideAShortWindowAndAlignsToTheAnchorEndInRtl() {
        var available = -1
        val position = AnchoredActionPosition(gap = 6, edge = 8) { available = it }
        assertEquals(
            IntOffset(100, 8),
            position.calculatePosition(
                IntRect(200, 110, 340, 158), IntSize(400, 260), LayoutDirection.Rtl, panel
            )
        )
        assertEquals(96, available)
        assertEquals(
            IntOffset(100, 8),
            position.calculatePosition(
                IntRect(200, 110, 340, 158), IntSize(400, 260), LayoutDirection.Rtl, IntSize(240, available)
            )
        )
    }
}
