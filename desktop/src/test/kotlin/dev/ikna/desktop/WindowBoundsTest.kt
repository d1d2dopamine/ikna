package dev.ikna.desktop

import java.awt.Rectangle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowBoundsTest {
    private val primary = Rectangle(0, 0, 1920, 1080)

    @Test
    fun `window on primary display is recoverable`() {
        assertTrue(hasRecoverableTitleBar(Rectangle(120, 100, 1180, 800), listOf(primary)))
    }

    @Test
    fun `window on a left hand display keeps valid negative coordinates`() {
        val left = Rectangle(-1920, 0, 1920, 1080)
        assertTrue(
            hasRecoverableTitleBar(
                Rectangle(-1700, 80, 1180, 800),
                listOf(left, primary)
            )
        )
    }

    @Test
    fun `saved position on disconnected right display is not recoverable`() {
        assertFalse(
            hasRecoverableTitleBar(
                Rectangle(2560, 120, 1180, 800),
                listOf(primary)
            )
        )
    }

    @Test
    fun `tiny one pixel overlap does not count as recoverable`() {
        assertFalse(
            hasRecoverableTitleBar(
                Rectangle(1919, 100, 1180, 800),
                listOf(primary)
            )
        )
    }

    @Test
    fun `partially offscreen window remains recoverable when title bar can be grabbed`() {
        assertTrue(
            hasRecoverableTitleBar(
                Rectangle(-900, 100, 1180, 800),
                listOf(primary)
            )
        )
    }

    @Test
    fun `display above primary is supported`() {
        val above = Rectangle(0, -1200, 1920, 1200)
        assertTrue(
            hasRecoverableTitleBar(
                Rectangle(140, -1100, 1180, 800),
                listOf(above, primary)
            )
        )
    }
}
