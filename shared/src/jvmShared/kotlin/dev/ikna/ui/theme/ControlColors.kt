package dev.ikna.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Quiet containers are not text ink. In particular, dark-mode buttons must not
 * turn into near-white rectangles. These roles keep the palette's hue, with
 * contrast reserved for letters, thin boundaries and the small moving thumb.
 * No extra preference and no platform-specific palette are needed.
 */
data class IknaControlColors(
    val fill: Color,
    val hover: Color,
    val pressed: Color,
    val idle: Color,
    val label: Color,
    val quietLabel: Color,
    val outline: Color,
    val mark: Color
)

val LocalIknaControlColors = staticCompositionLocalOf { controlColors(DarkPalette) }

internal const val CONTROL_FILL_DARK = 0.12f
internal const val CONTROL_FILL_LIGHT = 0.06f
internal const val CONTROL_HOVER_DARK = 0.15f
internal const val CONTROL_HOVER_LIGHT = 0.08f
internal const val CONTROL_PRESS_DARK = 0.18f
internal const val CONTROL_PRESS_LIGHT = 0.10f
internal const val CONTROL_IDLE_MIX = 0.035f
private const val MARK_CONTRAST = 3.15
private const val QUIET_TEXT_CONTRAST = 4.6

/** Called once per palette by IknaTheme; not once per frame or per control. */
fun controlColors(p: IknaPalette): IknaControlColors {
    val fill = readableFill(p, p.accent, if (p.light) CONTROL_FILL_LIGHT else CONTROL_FILL_DARK)
    val hover = readableFill(p, p.accent, if (p.light) CONTROL_HOVER_LIGHT else CONTROL_HOVER_DARK)
    val pressed = readableFill(p, p.accent, if (p.light) CONTROL_PRESS_LIGHT else CONTROL_PRESS_DARK)
    val idle = readableFill(p, p.ink, CONTROL_IDLE_MIX)
    val surfaces = listOf(p.background, fill, hover, pressed, idle)
    return IknaControlColors(
        fill = fill,
        hover = hover,
        pressed = pressed,
        idle = idle,
        label = p.ink,
        quietLabel = quietMark(p.muted, p.ink, surfaces, QUIET_TEXT_CONTRAST),
        outline = quietMark(p.muted, p.ink, surfaces, MARK_CONTRAST),
        mark = quietMark(p.accent, p.ink, surfaces, MARK_CONTRAST)
    )
}

/** A valid custom ink/background pair must stay readable on the derived fill. */
private fun readableFill(p: IknaPalette, tint: Color, amount: Float): Color {
    val desired = blend(p.background, tint, amount)
    if (contrastRatio(p.ink, desired) >= MIN_READABLE_CONTRAST) return desired
    var low = 0f
    var high = amount
    repeat(20) {
        val mid = (low + high) / 2f
        if (contrastRatio(p.ink, blend(p.background, tint, mid)) >= MIN_READABLE_CONTRAST) {
            low = mid
        } else high = mid
    }
    return blend(p.background, tint, low)
}

/**
 * The least bright/dark mark that clears every interactive surface. Prefer the
 * authored hue, then ink. Black/white are calculation endpoints for an invalid
 * custom pair, never the normal fill of a button, chip or switch.
 */
private fun quietMark(preferred: Color, ink: Color, surfaces: List<Color>, minimum: Double): Color {
    fun clears(color: Color): Boolean = surfaces.all { contrastRatio(color, it) >= minimum }
    val target = when {
        clears(preferred) -> preferred
        clears(ink) -> ink
        else -> listOf(ink, Color.Black, Color.White).maxByOrNull { color ->
            surfaces.minOf { contrastRatio(color, it) }
        } ?: ink
    }
    if (!clears(target)) return target
    var low = 0f
    var high = 1f
    repeat(20) {
        val mid = (low + high) / 2f
        if (clears(blend(surfaces.first(), target, mid))) high = mid else low = mid
    }
    return blend(surfaces.first(), target, high)
}

/** Explicit sRGB-channel mixing; the offline audit uses the same arithmetic. */
private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)
