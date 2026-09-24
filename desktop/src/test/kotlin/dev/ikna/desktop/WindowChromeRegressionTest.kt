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

    @Test
    fun `window resize does not relaunch an effect for every bounds event`() {
        val source = source("Main.kt")
        assertTrue(source.contains("LaunchedEffect(windowState.placement)"))
        assertTrue(!source.contains("LaunchedEffect(windowState.placement, windowState.size, windowState.position)"))
    }

    @Test
    fun `restore bounds are deferred until placement is floating`() {
        val source = source("Main.kt")
        assertTrue(source.contains("geometryMemory.requestRestore()"))
        assertTrue(source.contains("geometryMemory.restoreAfterPlacement(windowState)"))
        assertTrue(!source.contains("geometryMemory.restore(windowState)"))
    }

    @Test
    fun `offscreen recovery runs once on the native window path`() {
        val source = source("Main.kt")
        assertTrue(source.contains("recoverOffScreenWindow(window)"))
        assertTrue(source.contains("geometryMemory.forgetPosition()"))
        assertTrue(!source.contains("windowState.position, attachedScreenBounds"))
    }

    @Test
    fun `native resizable style stays stable across placement changes`() {
        val source = source("Main.kt")
        assertTrue(source.contains("WindowDecoration.Undecorated("))
        assertTrue(source.contains("WindowDecorationDefaults.ResizerThickness"))
        assertTrue(source.contains("0.dp"))
        assertTrue(source.contains("resizable = true"))
        assertTrue(!source.contains("resizable = !customTitleBar || windowState.placement"))
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
