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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckBackground
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val TAU = (PI * 2.0).toFloat()

/**
 * Exact 6x7 Ikna pixel emblem from the brand banner.
 * '#' = primary ink/slate cell, 'A' = accent cell, '.' = empty.
 */
private val IKNA_PIXEL_EMBLEM: Array<String> = arrayOf(
    "###.#A",
    "#.##.#",
    ".##.A#",
    "A..##.",
    "##..#.",
    "..#.#.",
    "..A..."
)

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

/**
 * Tilted, repeating Ikna pixel-emblem background pattern inspired by the brand banner.
 * Gently drifts along its diagonal axis when background animation is enabled, and
 * freezes cleanly with zero redraws when disabled.
 */
@Composable
fun IknaBrandPatternBackdrop(
    modifier: Modifier = Modifier,
    animated: Boolean = LocalIknaDeckBackgroundAnimated.current,
    intensity: Float = 1f
) {
    val colors = MaterialTheme.colorScheme
    val phase = rememberBackdropPhase(animated, durationMillis = 26000, label = "brand-pattern")
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val cell = 7.dp.toPx()
        val pitchX = 112.dp.toPx()
        val pitchY = 104.dp.toPx()
        // Alternating rows are staggered by pitchX / 2, so drifting by (pitchX / 2, pitchY)
        // maps every row seamlessly onto the next row when phase wraps from 1 to 0.
        val driftX = if (animated) phase * (pitchX * 0.5f) else 0f
        val driftY = if (animated) phase * pitchY else 0f

        val inkCell = lerp(colors.background, colors.onSurface, 0.085f * intensity)
        val accentCell = lerp(colors.background, colors.primary, 0.20f * intensity)

        val diagonal = hypot(size.width, size.height)
        val halfSpan = diagonal * 0.65f
        val cols = ceil((halfSpan * 2f) / pitchX).toInt() + 2
        val rows = ceil((halfSpan * 2f) / pitchY).toInt() + 2
        val originX = size.width * 0.5f - (cols / 2) * pitchX
        val originY = size.height * 0.5f - (rows / 2) * pitchY

        rotate(degrees = -14f, pivot = Offset(size.width * 0.5f, size.height * 0.5f)) {
            for (row in -1..rows) {
                val stagger = if (Math.floorMod(row, 2) == 0) 0f else pitchX * 0.5f
                val baseY = originY + row * pitchY + driftY
                for (col in -1..cols) {
                    val baseX = originX + col * pitchX + stagger + driftX
                    for (r in IKNA_PIXEL_EMBLEM.indices) {
                        val patternRow = IKNA_PIXEL_EMBLEM[r]
                        for (c in patternRow.indices) {
                            val ch = patternRow[c]
                            if (ch == '.') continue
                            drawRect(
                                color = if (ch == 'A') accentCell else inkCell,
                                topLeft = Offset(baseX + c * cell, baseY + r * cell),
                                size = Size(cell + 0.5f, cell + 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
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
            val topMix = 0.44f + 0.07f * wave
            val midStop = (0.55f + 0.10f * wave).coerceIn(0.32f, 0.78f)
            val bottomMix = 0.16f - 0.04f * wave
            Box(
                fill.background(
                    Brush.verticalGradient(
                        0f to lerp(colors.background, colors.surface, topMix),
                        midStop to colors.background,
                        1f to lerp(colors.background, colors.surfaceVariant, bottomMix)
                    )
                )
            ) {
                IknaBrandPatternBackdrop(animated = animated, intensity = 0.92f)
            }
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
                val line = colors.onSurfaceVariant.copy(alpha = 0.13f)
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
fun IknaLatticePlaceholder(
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val animated = loading && LocalIknaMotionEnabled.current
    val phase = rememberBackdropPhase(animated, durationMillis = 1600, label = "lattice-loading")
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
                val baseAlpha = if (row == 1) 0.20f else 0.11f
                val wave = if (animated) {
                    0.5f + 0.5f * sin(TAU * phase - column * 0.55f - row * 0.35f)
                } else {
                    0f
                }
                val color = if (animated && row == 1) {
                    lerp(ink, accent, wave * 0.45f).copy(alpha = baseAlpha + 0.22f * wave)
                } else {
                    ink.copy(alpha = if (animated) baseAlpha + 0.14f * wave else baseAlpha)
                }
                drawRect(
                    color = color,
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
