package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckBackground

@Composable
fun IknaHomeBackdrop(background: DeckBackground, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill = modifier.fillMaxSize()
    when (background) {
        DeckBackground.CLASSIC -> {
            Box(fill.background(colors.background)) {
                IknaMemoryField(seed = 0x1A4B_7C2D)
            }
        }
        DeckBackground.TONAL -> Box(
            fill.background(
                Brush.verticalGradient(
                    0f to lerp(colors.background, colors.surface, 0.48f),
                    0.55f to colors.background,
                    1f to lerp(colors.background, colors.surfaceVariant, 0.16f)
                )
            )
        )
        DeckBackground.SOLID -> Box(fill.background(colors.background))
        DeckBackground.GLOW -> Canvas(fill) {
            drawRect(Brush.radialGradient(
                colors = listOf(lerp(colors.background, colors.primary, 0.16f), colors.background),
                center = Offset(size.width * 0.82f, size.height * 0.20f),
                radius = size.maxDimension.coerceAtLeast(1f) * 0.72f
            ))
        }
        DeckBackground.GRID -> Canvas(fill.background(colors.background)) {
            val pitch = 24.dp.toPx()
            val line = colors.onSurfaceVariant.copy(alpha = 0.13f)
            for (column in 1..(size.width / pitch).toInt()) {
                val x = column * pitch
                drawLine(line, Offset(x, 0f), Offset(x, size.height))
            }
            for (row in 1..(size.height / pitch).toInt()) {
                val y = row * pitch
                drawLine(line, Offset(0f, y), Offset(size.width, y))
            }
        }
    }
}

/** A dense, low-contrast, deterministic pixel grain for unused screen regions. */
@Composable
private fun IknaMemoryField(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val pitch = 7.dp.toPx()
        val dot = 1.25.dp.toPx()
        val jitter = 0.45.dp.toPx()
        val columns = (size.width / pitch).toInt().coerceAtLeast(1)
        val rows = (size.height / pitch).toInt().coerceAtLeast(1)
        val firstRow = (rows * 0.24f).toInt().coerceAtMost(rows - 1)
        var state = seed xor 0x4B1D5A77

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
                val alpha = when ((state ushr 24) and 3) {
                    0 -> 0.130f
                    1 -> 0.095f
                    else -> 0.065f
                }
                val x = (
                    column * pitch + ((state ushr 8) and 3) * jitter
                ).coerceIn(0f, (size.width - markWidth).coerceAtLeast(0f))
                val y = (
                    row * pitch + ((state ushr 10) and 3) * jitter
                ).coerceIn(0f, (size.height - markHeight).coerceAtLeast(0f))

                drawRect(
                    color = ink.copy(alpha = alpha),
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
