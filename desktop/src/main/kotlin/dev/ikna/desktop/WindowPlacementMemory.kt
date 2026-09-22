package dev.ikna.desktop

/**
 * Window placement history that Compose does not keep for us.
 *
 * Fullscreen is deliberately transient: F11 should return to the placement the
 * user had before entering it, and closing while fullscreen should persist that
 * same non-fullscreen placement rather than treating fullscreen as floating.
 */
internal enum class DesktopWindowPlacement { FLOATING, MAXIMIZED, FULLSCREEN }

internal class WindowPlacementMemory(initial: DesktopWindowPlacement) {
    var lastNonFullscreen: DesktopWindowPlacement =
        if (initial == DesktopWindowPlacement.FULLSCREEN) DesktopWindowPlacement.FLOATING else initial
        private set

    fun observe(current: DesktopWindowPlacement) {
        if (current != DesktopWindowPlacement.FULLSCREEN) lastNonFullscreen = current
    }

    fun toggleMaximize(current: DesktopWindowPlacement): DesktopWindowPlacement {
        val target = if (current == DesktopWindowPlacement.MAXIMIZED) {
            DesktopWindowPlacement.FLOATING
        } else {
            DesktopWindowPlacement.MAXIMIZED
        }
        observe(target)
        return target
    }

    fun toggleFullscreen(current: DesktopWindowPlacement): DesktopWindowPlacement {
        if (current == DesktopWindowPlacement.FULLSCREEN) return lastNonFullscreen
        lastNonFullscreen = current
        return DesktopWindowPlacement.FULLSCREEN
    }

    fun persisted(current: DesktopWindowPlacement): DesktopWindowPlacement =
        if (current == DesktopWindowPlacement.FULLSCREEN) lastNonFullscreen else current
}
