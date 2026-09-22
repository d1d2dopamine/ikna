package dev.ikna.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowChromeRegressionTest {
    @Test
    fun `maximized custom title bar is not draggable`() {
        val source = source("desktop/src/main/kotlin/dev/ikna/desktop/WindowTitleBar.kt")
        assertTrue(source.contains("if (state.placement == WindowPlacement.Floating)"))
        assertTrue(source.contains("WindowDraggableArea(modifier = titleModifier)"))
        assertTrue(source.contains("Box(modifier = titleModifier) { titleContent() }"))
    }

    @Test
    fun `saved window geometry uses floating bounds`() {
        val source = source("desktop/src/main/kotlin/dev/ikna/desktop/Main.kt")
        assertTrue(source.contains("geometryMemory.floatingSize"))
        assertTrue(source.contains("geometryMemory.floatingPosition"))
        assertTrue(source.contains("placementMemory.persisted(current)"))
    }

    private fun source(path: String): String = File(path).readText()
}
