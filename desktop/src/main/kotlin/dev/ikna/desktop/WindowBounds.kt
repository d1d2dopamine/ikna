package dev.ikna.desktop

import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import kotlin.math.min

private const val MIN_RECOVERABLE_TITLE_WIDTH = 96
private const val MIN_RECOVERABLE_TITLE_HEIGHT = 24
private const val TITLE_STRIP_HEIGHT = 56

/**
 * A floating desktop window is recoverable only when enough of its top edge is
 * visible on at least one attached display to grab and move it normally.
 *
 * Screen coordinates are deliberately allowed to be negative: Windows and
 * Linux both use them for displays placed left of or above the primary screen.
 */
internal fun hasRecoverableTitleBar(
    windowBounds: Rectangle,
    screenBounds: List<Rectangle>
): Boolean {
    if (screenBounds.isEmpty()) return true
    if (windowBounds.width <= 0 || windowBounds.height <= 0) return false

    val titleStrip = Rectangle(
        windowBounds.x,
        windowBounds.y,
        windowBounds.width,
        min(TITLE_STRIP_HEIGHT, windowBounds.height)
    )
    return screenBounds.any { screen ->
        val visible = titleStrip.intersection(screen)
        !visible.isEmpty &&
            visible.width >= MIN_RECOVERABLE_TITLE_WIDTH &&
            visible.height >= MIN_RECOVERABLE_TITLE_HEIGHT
    }
}

internal fun attachedScreenBounds(): List<Rectangle> = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment()
        .screenDevices
        .map { Rectangle(it.defaultConfiguration.bounds) }
}.getOrDefault(emptyList())

/**
 * Repairs geometry only after the native window exists, so the comparison and
 * the move use one coordinate system even on mixed-DPI multi-monitor setups.
 */
internal fun recoverOffScreenWindow(window: Window): Boolean {
    val screens = attachedScreenBounds()
    if (hasRecoverableTitleBar(window.bounds, screens)) return false

    val target = runCatching {
        Rectangle(
            GraphicsEnvironment.getLocalGraphicsEnvironment()
                .defaultScreenDevice.defaultConfiguration.bounds
        )
    }.getOrNull() ?: screens.firstOrNull() ?: return false

    val x = target.x + (target.width - window.width).coerceAtLeast(0) / 2
    val y = target.y + (target.height - window.height).coerceAtLeast(0) / 2
    window.setLocation(x, y)
    return true
}
