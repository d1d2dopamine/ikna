package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale
import kotlin.math.pow

/*
 * Contrast arithmetic.
 *
 * It lives outside the theme file because two unrelated callers need it. The
 * custom-colour editor has to be able to say "this pair is unreadable" while the
 * user is still choosing, and the system bars have to pick black or white icons
 * from the actual background colour rather than from the phone's dark mode
 * switch — the two stopped agreeing the moment the app got its own light/dark
 * setting, which is how you end up with white status icons on a white screen.
 */

/** WCAG 2.1 relative luminance, 0 (black) to 1 (white). */
fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}

/** WCAG contrast ratio: 1.0 for two identical colours, 21.0 for black on white. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = if (la > lb) la else lb
    val lo = if (la > lb) lb else la
    return (hi + 0.05) / (lo + 0.05)
}

/** The line below which body text stops being readable. */
const val MIN_READABLE_CONTRAST = 4.5

/**
 * Whether a background needs dark icons drawn on it.
 *
 * The threshold sits above 0.5 on purpose: mid-tones are ambiguous, and a wrong
 * guess costs an invisible status bar, so the darker half of the ambiguous range
 * keeps light icons.
 */
fun isLight(color: Color): Boolean = relativeLuminance(color) > 0.45

fun ratioText(ratio: Double): String = String.format(Locale.US, "%.1f:1", ratio)

/**
 * "#1B1813", "1b1813" -> Color. Null for anything that is not six hex digits,
 * so a half-typed value simply does not apply yet instead of throwing.
 */
fun parseHexColor(text: String): Color? {
    val body = text.trim().removePrefix("#")
    if (body.length != 6) return null
    if (!body.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = body.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or value).toInt())
}

/** Six upper-case hex digits, no leading hash. */
fun hexOf(color: Color): String =
    String.format(Locale.US, "%06X", color.toArgb() and 0xFFFFFF)
