package dev.ikna.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowChromeRegressionTest {
    @Test
    fun `maximized custom title bar is not draggable`() {
        val source = source("WindowTitleBar.kt")
        assertTrue(source.contains("if (state.placement == WindowPlacement.Floating)"))
        assertTrue(source.contains("WindowDraggableArea(modifier = titleModifier)"))
        assertTrue(source.contains("Box(modifier = titleModifier) { titleContent() }"))
    }

    @Test
    fun `saved window geometry uses floating bounds`() {
        val source = source("Main.kt")
        assertTrue(source.contains("geometryMemory.floatingSize"))
        assertTrue(source.contains("geometryMemory.floatingPosition"))
        assertTrue(source.contains("placementMemory.persisted(current)"))
    }

    private fun source(name: String): String {
        val start = File(System.getProperty("user.dir")).canonicalFile
        val file = generateSequence(start) { it.parentFile }
            .map { File(it, "desktop/src/main/kotlin/dev/ikna/desktop/$name") }
            .firstOrNull { it.isFile }
            ?: error("Could not locate desktop source $name from ${start.path}")
        return file.readText()
    }
}
