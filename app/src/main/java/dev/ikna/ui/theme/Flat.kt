package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.window.Dialog

/*
 * The parts Material would otherwise decide for us.
 *
 * Everything here is a rectangle drawn by hand: no elevation, no ripple shapes,
 * no icon library. Two reasons. Flat right angles are the look, and a geometric
 * glyph drawn in ten lines of Canvas never looks like a placeholder the way a
 * character from a font does.
 *
 * Why this file has to cover buttons, switches, chips and dialogs as well:
 * a Material 3 component does not take its shape from MaterialTheme.shapes. It
 * reads its own token, and for every button, switch and chip that token is
 * CornerFull, which is hardcoded to CircleShape. Setting a square shape scheme
 * in the theme therefore does nothing to them. One stock Button left on a screen
 * is enough to make the app look like two apps stitched together, which is
 * exactly what it looked like. Nothing below can round itself behind our back.
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
 *
 * [quiet] dims the outline and the label without changing the geometry, so a
 * rarely used answer can sit next to a common one without competing with it.
 */
@Composable
fun IknaWideButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    quiet: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 60.dp
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    val alpha = (if (enabled) 1f else 0.35f) * (if (quiet) 0.6f else 1f)

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
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * A text action with no container at all.
 *
 * Replaces TextButton, which draws no background either but still reserves a
 * pill-shaped ripple and a 20dp minimum corner radius around itself.
 */
@Composable
fun IknaTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/**
 * A switch made of two rectangles.
 *
 * The knob does not slide and does not animate: the state is read from where the
 * filled block sits, and an animation on a control this small only makes the
 * current state ambiguous for 150ms.
 */
@Composable
fun IknaToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .width(54.dp)
            .height(30.dp)
            .border(1.dp, ink.copy(alpha = 0.55f * alpha))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 22.dp)
                .background(
                    if (checked) ink.copy(alpha = alpha)
                    else ink.copy(alpha = 0.22f * alpha)
                )
        )
    }
}

/** Square chip. Selected means filled, not tinted and not outlined-in-accent. */
@Composable
fun IknaChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .height(38.dp)
            .background(if (selected) ink else Color.Transparent)
            .border(1.dp, if (selected) ink else IknaLine.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) paper else ink,
            maxLines = 1
        )
    }
}

/**
 * A dialog that is a rectangle on the background colour.
 *
 * AlertDialog is the one Material component that does read the theme's shape
 * scheme, but it also brings a tonal surface, a 6dp elevation and pill-shaped
 * text buttons, so it is easier to draw than to argue with.
 */
@Composable
fun IknaDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmColor: Color = MaterialTheme.colorScheme.onBackground
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = IknaMuted
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaTextButton(label = dismissLabel, onClick = onDismiss, color = IknaMuted)
                Spacer(Modifier.width(18.dp))
                IknaTextButton(label = confirmLabel, onClick = onConfirm, color = confirmColor)
            }
        }
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
 * Progress: one filled band, no rounding, no animation.
 *
 * No track by default. An empty track is a full-width grey strip pinned to the
 * top of the screen for the whole of the first card, which reads as a piece of
 * chrome rather than as progress, and it was the first thing anyone noticed
 * about the session screen. With the track gone, zero progress draws nothing.
 *
 * Deliberately unlabelled. A number that counts up invites arithmetic about how
 * much is left, and that arithmetic is where the wanting-to-stop starts.
 */
@Composable
fun IknaProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Boolean = false
) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(if (track) IknaLine.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safe)
                .height(height)
                .background(color)
        )
    }
}
