package dev.ikna.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckBackground
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAU = (PI * 2.0).toFloat()

@Composable
private fun rememberBackdropPhase(animated: Boolean, durationMillis: Int, label: String): Float {
    if (!animated) return 0f
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "$label-phase"
    )
    return phase
}

@Composable
fun IknaHomeBackdrop(
    background: DeckBackground,
    modifier: Modifier = Modifier,
    animated: Boolean = LocalIknaDeckBackgroundAnimated.current
) {
    val colors = MaterialTheme.colorScheme
    val fill = modifier.fillMaxSize()
    when (background) {
        DeckBackground.CLASSIC -> {
            val phase = rememberBackdropPhase(animated, durationMillis = 9000, label = "classic-backdrop")
            Box(fill.background(colors.background)) {
                IknaMemoryField(seed = 0x1A4B_7C2D, animated = animated, phase = phase)
            }
        }
        DeckBackground.TONAL -> {
            val phase = rememberBackdropPhase(animated, durationMillis = 12000, label = "tonal-backdrop")
            val wave = if (animated) sin(TAU * phase) else 0f
            val topMix = 0.48f + 0.08f * wave
            val midStop = (0.55f + 0.11f * wave).coerceIn(0.32f, 0.78f)
            val bottomMix = 0.16f - 0.04f * wave
            Box(
                fill.background(
                    Brush.verticalGradient(
                        0f to lerp(colors.background, colors.surface, topMix),
                        midStop to colors.background,
                        1f to lerp(colors.background, colors.surfaceVariant, bottomMix)
                    )
                )
            )
        }
        DeckBackground.SOLID -> Box(fill.background(colors.background))
        DeckBackground.GLOW -> {
            val phase = rememberBackdropPhase(animated, durationMillis = 14000, label = "glow-backdrop")
            val orbitX = if (animated) 0.055f * cos(TAU * phase) else 0f
            val orbitY = if (animated) 0.045f * sin(TAU * phase) else 0f
            val breathe = if (animated) 0.08f * sin(TAU * phase) else 0f
            val glowMix = 0.16f + if (animated) 0.03f * cos(TAU * phase) else 0f
            Canvas(fill) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(lerp(colors.background, colors.primary, glowMix), colors.background),
                        center = Offset(size.width * (0.82f + orbitX), size.height * (0.20f + orbitY)),
                        radius = size.maxDimension.coerceAtLeast(1f) * (0.72f + breathe)
                    )
                )
            }
        }
        DeckBackground.GRID -> {
            val phase = rememberBackdropPhase(animated, durationMillis = 16000, label = "grid-backdrop")
            Canvas(fill.background(colors.background)) {
                val pitch = 24.dp.toPx()
                val drift = if (animated) phase * pitch else 0f
                val pulse = if (animated) 0.5f + 0.5f * sin(TAU * phase) else 0f
                val line = colors.onSurfaceVariant.copy(alpha = 0.12f + 0.025f * pulse)
                val columns = (size.width / pitch).toInt() + 1
                val rows = (size.height / pitch).toInt() + 1
                for (column in 0..columns) {
                    val x = column * pitch + drift
                    if (x in 0f..size.width) {
                        drawLine(line, Offset(x, 0f), Offset(x, size.height))
                    }
                }
                for (row in 0..rows) {
                    val y = row * pitch + drift
                    if (y in 0f..size.height) {
                        drawLine(line, Offset(0f, y), Offset(size.width, y))
                    }
                }
                if (animated) {
                    val bandY = size.height * (0.15f + 0.70f * (0.5f + 0.5f * sin(TAU * phase)))
                    val band = Brush.verticalGradient(
                        0f to colors.primary.copy(alpha = 0f),
                        0.5f to colors.primary.copy(alpha = 0.055f),
                        1f to colors.primary.copy(alpha = 0f),
                        startY = (bandY - pitch * 3f).coerceAtLeast(0f),
                        endY = (bandY + pitch * 3f).coerceAtMost(size.height.coerceAtLeast(1f))
                    )
                    drawRect(band)
                }
            }
        }
    }
}

/** A dense, low-contrast, deterministic pixel grain for unused screen regions. */
@Composable
private fun IknaMemoryField(
    seed: Int,
    animated: Boolean = false,
    phase: Float = 0f,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize()) {
        val pitch = 7.dp.toPx()
        val dot = 1.25.dp.toPx()
        val jitter = 0.45.dp.toPx()
        val columns = (size.width / pitch).toInt().coerceAtLeast(1)
        val rows = (size.height / pitch).toInt().coerceAtLeast(1)
        val firstRow = (rows * 0.24f).toInt().coerceAtMost(rows - 1)
        var state = seed xor 0x4B1D5A77
        val angleBase = TAU * phase

        for (row in firstRow until rows) {
            for (column in 0 until columns) {
                state = fieldStep(state + row * 131 + column * 53)
                val gate = state ushr 28
                if (gate >= 8) continue

                val kind = (state ushr 4) and 15
                val markWidth = when (kind) {
                    0, 1 -> dot * 3f
                    2, 3, 4 -> dot * 2f
                    else -> dot
                }
                val markHeight = when (kind) {
                    5 -> dot * 3f
                    6, 7 -> dot * 2f
                    else -> dot
                }
                val baseAlpha = when ((state ushr 24) and 3) {
                    0 -> 0.130f
                    1 -> 0.095f
                    else -> 0.065f
                }
                val isAccentGlint = animated && ((state ushr 16) and 31) == 0
                val wave = if (animated) {
                    0.5f + 0.5f * sin(angleBase - row * 0.18f - column * 0.11f + ((state ushr 12) and 7) * 0.4f)
                } else {
                    0.5f
                }
                val color = if (isAccentGlint) {
                    accent.copy(alpha = 0.04f + 0.14f * wave)
                } else {
                    val alpha = if (animated) baseAlpha * (0.62f + 0.66f * wave) else baseAlpha
                    ink.copy(alpha = alpha)
                }
                val driftY = if (animated) (wave - 0.5f) * dot * 0.85f else 0f
                val x = (
                    column * pitch + ((state ushr 8) and 3) * jitter
                ).coerceIn(0f, (size.width - markWidth).coerceAtLeast(0f))
                val y = (
                    row * pitch + ((state ushr 10) and 3) * jitter + driftY
                ).coerceIn(0f, (size.height - markHeight).coerceAtLeast(0f))

                drawRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(markWidth, markHeight)
                )
            }
        }
    }
}

/** A compact unfinished lattice used instead of a dash or a blank chart. */
@Composable
fun IknaLatticePlaceholder(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val columns = 11
        val rows = 3
        val gap = 6.dp.toPx()
        val cell = ((size.width - gap * (columns - 1)) / columns).coerceAtMost(14.dp.toPx())
        val used = cell * columns + gap * (columns - 1)
        val left = (size.width - used) / 2f
        val rowStep = (size.height - cell) / (rows - 1)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val present = ((column * 5 + row * 7) % 9 < 3) ||
                    (row == 1 && column in 3..7)
                if (!present) continue
                drawRect(
                    color = ink.copy(alpha = if (row == 1) 0.20f else 0.11f),
                    topLeft = Offset(left + column * (cell + gap), row * rowStep),
                    size = Size(cell, cell)
                )
            }
        }
    }
}

/** A tiny integer generator: deterministic on every Android and JVM version. */
private fun fieldStep(value: Int): Int {
    var x = value
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    return x
}
