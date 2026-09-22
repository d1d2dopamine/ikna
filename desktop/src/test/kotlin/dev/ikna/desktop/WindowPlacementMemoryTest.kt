package dev.ikna.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowPlacementMemoryTest {
    @Test
    fun `fullscreen returns to floating`() {
        val memory = WindowPlacementMemory(DesktopWindowPlacement.FLOATING)
        assertEquals(DesktopWindowPlacement.FULLSCREEN, memory.toggleFullscreen(DesktopWindowPlacement.FLOATING))
        assertEquals(DesktopWindowPlacement.FLOATING, memory.toggleFullscreen(DesktopWindowPlacement.FULLSCREEN))
    }

    @Test
    fun `fullscreen returns to maximized`() {
        val memory = WindowPlacementMemory(DesktopWindowPlacement.MAXIMIZED)
        assertEquals(DesktopWindowPlacement.FULLSCREEN, memory.toggleFullscreen(DesktopWindowPlacement.MAXIMIZED))
        assertEquals(DesktopWindowPlacement.MAXIMIZED, memory.toggleFullscreen(DesktopWindowPlacement.FULLSCREEN))
    }

    @Test
    fun `fullscreen persists previous non fullscreen placement`() {
        val memory = WindowPlacementMemory(DesktopWindowPlacement.FLOATING)
        memory.toggleFullscreen(DesktopWindowPlacement.FLOATING)
        assertEquals(DesktopWindowPlacement.FLOATING, memory.persisted(DesktopWindowPlacement.FULLSCREEN))

        memory.observe(DesktopWindowPlacement.MAXIMIZED)
        memory.toggleFullscreen(DesktopWindowPlacement.MAXIMIZED)
        assertEquals(DesktopWindowPlacement.MAXIMIZED, memory.persisted(DesktopWindowPlacement.FULLSCREEN))
    }

    @Test
    fun `maximize toggles between maximized and floating`() {
        val memory = WindowPlacementMemory(DesktopWindowPlacement.FLOATING)
        assertEquals(DesktopWindowPlacement.MAXIMIZED, memory.toggleMaximize(DesktopWindowPlacement.FLOATING))
        assertEquals(DesktopWindowPlacement.FLOATING, memory.toggleMaximize(DesktopWindowPlacement.MAXIMIZED))
    }
}
