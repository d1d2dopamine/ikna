package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The parts Material would otherwise decide for us.
 *
 * Everything here is a rectangle drawn by hand: no elevation, no ripple shapes,
 * no icon library. Two reasons. Flat right angles are the look, and a geometric
 * glyph drawn in ten lines of Canvas never looks like a placeholder the way a
 * character from a font does.
 */

/** Tab marks. Primitives on purpose — they read as a set instead of as clip-art. */
enum class IknaGlyph { SPARK, STACK, BARS, SLIDERS }

@Composable
fun IknaGlyphIcon(
    glyph: IknaGlyph,
    color: Color,
    size: Dp = 22.dp
) {
    Canvas(modifier = Modifier.size(size)) {
        val s = this.size.minDimension
        when (glyph) {
            // The app mark: same spark as the launcher icon.
            IknaGlyph.SPARK -> {
                val path = Path().apply {
                    moveTo(s * 0.5f, 0f)
                    quadraticBezierTo(s * 0.57f, s * 0.43f, s, s * 0.5f)
                    quadraticBezierTo(s * 0.57f, s * 0.57f, s * 0.5f, s)
                    quadraticBezierTo(s * 0.43f, s * 0.57f, 0f, s * 0.5f)
                    quadraticBezierTo(s * 0.43f, s * 0.43f, s * 0.5f, 0f)
                    close()
                }
                drawPath(path, color)
            }
            // Decks: three stacked slabs.
            IknaGlyph.STACK -> {
                val h = s * 0.16f
                listOf(0f, s * 0.42f, s * 0.84f).forEach { y ->
                    drawRect(color, Offset(0f, y), Size(s, h))
                }
            }
            // Progress: bars of different heights, never a rising curve.
            IknaGlyph.BARS -> {
                val w = s * 0.22f
                val gap = (s - w * 3) / 2f
                listOf(0.45f, 0.75f, 1f).forEachIndexed { i, k ->
                    val barHeight = s * k
                    drawRect(
                        color,
                        Offset(i * (w + gap), s - barHeight),
                        Size(w, barHeight)
                    )
                }
            }
            // Settings: two rules with a square knob each. No cog.
            IknaGlyph.SLIDERS -> {
                val line = s * 0.1f
                val knob = s * 0.3f
                drawRect(color, Offset(0f, s * 0.22f), Size(s, line))
                drawRect(color, Offset(s * 0.58f, s * 0.22f - knob * 0.35f), Size(knob, knob))
                drawRect(color, Offset(0f, s * 0.68f), Size(s, line))
                drawRect(color, Offset(s * 0.12f, s * 0.68f - knob * 0.35f), Size(knob, knob))
            }
        }
    }
}

/**
 * Full-width rectangular button with a fixed height.
 *
 * Fixed height matters more than it looks: the answer buttons must never change
 * size between cards, or the target moves under a thumb that is already moving.
 */
@Composable
fun IknaWideButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 60.dp
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(if (filled) ink.copy(alpha = alpha) else Color.Transparent)
            .border(1.dp, ink.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = (if (filled) paper else ink).copy(alpha = alpha),
            textAlign = TextAlign.Center
        )
    }
}

/** A rule. Replaces every shadow and every card edge in the app. */
@Composable
fun IknaRule(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = IknaLine.copy(alpha = 0.35f)
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color)
    )
}

/**
 * Session progress: one filled band, no track rounding, no animation.
 *
 * Deliberately unlabelled. A number that counts up invites arithmetic about how
 * much is left, and that arithmetic is where the wanting-to-stop starts.
 */
@Composable
fun IknaProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    val safe = fraction.coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(IknaLine.copy(alpha = 0.2f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safe)
                .height(height)
                .background(accent)
        )
    }
}
