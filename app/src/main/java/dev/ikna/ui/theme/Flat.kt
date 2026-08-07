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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
 *
 * Nothing here reads a colour constant either. Every value comes from the
 * scheme, which is what lets a user-defined palette reach the switches and the
 * rules instead of stopping at the text.
 */

/**
 * Marks and controls. Primitives on purpose — they read as a set instead of as
 * clip-art, and a pulled-in icon font would bring Material's rounded geometry
 * back through the side door.
 */
enum class IknaGlyph { SPARK, STACK, BARS, SLIDERS, GEAR, PLUS, BACK, SOUND }

@Composable
fun IknaGlyphIcon(
    glyph: IknaGlyph,
    color: Color,
    size: Dp = 24.dp,
    /**
     * What a screen reader should call this mark. Null means decorative: the
     * glyph sits next to text that already says the same thing, and announcing
     * it twice is worse than not announcing it at all.
     */
    label: String? = null
) {
    // A hand-drawn Canvas has no text in it, so without this a screen reader
    // finds nothing to say and skips the control entirely. Every mark in this
    // app is drawn, which is why silence here means a silent interface.
    val described = if (label == null) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = label }
    }

    Canvas(modifier = Modifier.size(size).then(described)) {
        val s = this.size.minDimension
        when (glyph) {
            // The app mark: same spark as the launcher icon.
            IknaGlyph.SPARK -> {
                val path = Path().apply {
                    moveTo(s * 0.5f, 0f)
                    quadraticTo(s * 0.57f, s * 0.43f, s, s * 0.5f)
                    quadraticTo(s * 0.57f, s * 0.57f, s * 0.5f, s)
                    quadraticTo(s * 0.43f, s * 0.57f, 0f, s * 0.5f)
                    quadraticTo(s * 0.43f, s * 0.43f, s * 0.5f, 0f)
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
            // Two rules with a square knob each. Kept for anywhere that means
            // "adjust" rather than "settings".
            IknaGlyph.SLIDERS -> {
                val line = s * 0.1f
                val knob = s * 0.3f
                drawRect(color, Offset(0f, s * 0.22f), Size(s, line))
                drawRect(color, Offset(s * 0.58f, s * 0.22f - knob * 0.35f), Size(knob, knob))
                drawRect(color, Offset(0f, s * 0.68f), Size(s, line))
                drawRect(color, Offset(s * 0.12f, s * 0.68f - knob * 0.35f), Size(knob, knob))
            }
            // Settings: a cog with square teeth, because a round one would be the
            // only circle in the app.
            IknaGlyph.GEAR -> {
                val tooth = s * 0.17f
                val span = s * 0.2f
                val mid = (s - span) / 2f
                drawRect(color, Offset(mid, 0f), Size(span, tooth))
                drawRect(color, Offset(mid, s - tooth), Size(span, tooth))
                drawRect(color, Offset(0f, mid), Size(tooth, span))
                drawRect(color, Offset(s - tooth, mid), Size(tooth, span))
                drawRect(
                    color = color,
                    topLeft = Offset(s * 0.19f, s * 0.19f),
                    size = Size(s * 0.62f, s * 0.62f),
                    style = Stroke(width = s * 0.13f)
                )
            }
            // Add.
            IknaGlyph.PLUS -> {
                val bar = s * 0.14f
                val mid = (s - bar) / 2f
                drawRect(color, Offset(mid, 0f), Size(bar, s))
                drawRect(color, Offset(0f, mid), Size(s, bar))
            }
            // Speech. Angular on purpose — a rounded speaker would be the only
            // curve in the app.
            IknaGlyph.SOUND -> {
                val cone = Path().apply {
                    moveTo(s * 0.04f, s * 0.36f)
                    lineTo(s * 0.24f, s * 0.36f)
                    lineTo(s * 0.46f, s * 0.12f)
                    lineTo(s * 0.46f, s * 0.88f)
                    lineTo(s * 0.24f, s * 0.64f)
                    lineTo(s * 0.04f, s * 0.64f)
                    close()
                }
                drawPath(cone, color)
                drawRect(color, Offset(s * 0.60f, s * 0.34f), Size(s * 0.09f, s * 0.32f))
                drawRect(color, Offset(s * 0.78f, s * 0.22f), Size(s * 0.09f, s * 0.56f))
            }
            // Back out of a screen.
            IknaGlyph.BACK -> {
                val w = s * 0.12f
                val y = s * 0.5f
                drawLine(color, Offset(s, y), Offset(s * 0.06f, y), strokeWidth = w)
                drawLine(color, Offset(s * 0.06f, y), Offset(s * 0.46f, s * 0.12f), strokeWidth = w)
                drawLine(color, Offset(s * 0.06f, y), Offset(s * 0.46f, s * 0.88f), strokeWidth = w)
            }
        }
    }
}

/**
 * A glyph you can press.
 *
 * The touch target is 44dp square while the mark inside stays small, so the top
 * bar reads as quiet without being hard to hit. This is what replaced the
 * 20dp invisible strip at the left edge: that strip was the only way into the
 * navigation panel, and on Android 10 and later it is also where the system
 * takes its own back gesture from — the app lost that fight roughly every other
 * try.
 */
@Composable
fun IknaIconButton(
    glyph: IknaGlyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    glyphSize: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    /** The name a screen reader reads out. Every call site should pass one. */
    label: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .semantics {
                role = Role.Button
                if (label != null) contentDescription = label
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        IknaGlyphIcon(
            glyph = glyph,
            color = color.copy(alpha = if (enabled) 1f else 0.35f),
            size = glyphSize
        )
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
    height: Dp = 56.dp
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
            .padding(horizontal = 8.dp),
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
    enabled: Boolean = true,
    /**
     * A name for the switch itself. Only needed where the row around it does not
     * already say what is being switched - a deck row, for instance, is read out
     * as the deck's title, and the switch beside it would otherwise be announced
     * as a nameless control.
     */
    label: String? = null
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .width(56.dp)
            .height(32.dp)
            .border(1.dp, ink.copy(alpha = 0.55f * alpha))
            // toggleable rather than clickable: it is what tells the platform
            // this is a switch and what state it is in, so a screen reader says
            // "on" and "off" instead of announcing an anonymous button.
            .semantics { if (label != null) contentDescription = label }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 24.dp)
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
    val line = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) ink else Color.Transparent)
            .border(1.dp, if (selected) ink else line)
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
 * Six hex digits, typed.
 *
 * A colour picker is a wheel, a wheel is a circle, and there are no circles
 * here. A hex field is also the only control that can be checked for contrast
 * before it is applied, which is the part that actually matters when the user
 * can pick their own background.
 */
@Composable
fun IknaHexField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val line = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .height(40.dp)
            .border(1.dp, line)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { text -> onValueChange(text.take(7)) },
            textStyle = MaterialTheme.typography.labelLarge.copy(color = ink),
            singleLine = true,
            cursorBrush = SolidColor(ink),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** A colour, shown as a colour. Bordered so that black on black is still visible. */
@Composable
fun IknaSwatch(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    )
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.onBackground)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = muted
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaTextButton(label = dismissLabel, onClick = onDismiss, color = muted)
                Spacer(Modifier.width(20.dp))
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
    color: Color = MaterialTheme.colorScheme.outline
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
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(if (track) trackColor else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safe)
                .height(height)
                .background(color)
        )
    }
}
