package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/*
 * Empty space with a fine digital grain.
 *
 * This is not one route and never resolves into a snake. Hundreds of independent
 * square dots and very short orthogonal strokes form a quiet field, inspired by
 * plotter and terminal noise. The marks are deterministic, faint and concentrated
 * where the home screen is genuinely unused. The learning card never uses this
 * component: silence around a phrase is functional and stays completely clean.
 */

/** A dense, low-contrast, deterministic pixel grain for unused screen regions. */
@Composable
fun IknaMemoryField(
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

/**
 * Sparse pixel paint above the deck list, with unfinished runs falling down.
 *
 * The title and Today's whole reading area are protected rectangles: the field
 * frames those words but never competes with them. On desktop this canvas is
 * already clipped by the deck column, so no pixel can leak into the work pane.
 */
@Composable
fun IknaDeckHeaderPaint(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
    ) {
        val pitch = 6.dp.toPx()
        val pixel = 1.35.dp.toPx()
        val columns = (size.width / pitch).toInt().coerceAtLeast(1)
        val denseRows = (54.dp.toPx() / pitch).toInt().coerceAtLeast(1)
        val titleLeft = 14.dp.toPx()
        val titleRight = 190.dp.toPx()
        val titleTop = 7.dp.toPx()
        val titleBottom = 50.dp.toPx()
        val todayLeft = 12.dp.toPx()
        val todayRight = 196.dp.toPx()
        val todayTop = 51.dp.toPx()
        val todayBottom = 146.dp.toPx()
        // Preserve the successful upper-right cluster exactly. Every hidden
        // column still advances the deterministic generator; it simply does
        // not paint the isolated marks that used to sit on the left.
        val clusterLeft = size.width * 0.49f

        fun protected(x: Float, y: Float, width: Float, height: Float): Boolean {
            fun touches(left: Float, top: Float, right: Float, bottom: Float): Boolean =
                x < right && x + width > left && y < bottom && y + height > top
            return touches(titleLeft, titleTop, titleRight, titleBottom) ||
                touches(todayLeft, todayTop, todayRight, todayBottom)
        }

        var state = seed xor 0x72C4_19A5

        // A broken coat rather than a solid band. Density falls toward the edge,
        // which is what makes the lower marks read as wet paint, not wallpaper.
        for (row in 0 until denseRows) {
            val keep = when {
                row < denseRows / 3 -> 8
                row < denseRows * 2 / 3 -> 6
                else -> 3
            }
            for (column in 0 until columns) {
                state = fieldStep(state + row * 149 + column * 61)
                if ((state ushr 28) >= keep) continue

                val markWidth = pixel * (1 + ((state ushr 6) and 3))
                val markHeight = pixel * (1 + ((state ushr 10) and 1))
                val x = (column * pitch + ((state ushr 12) and 3) * pixel * 0.24f)
                    .coerceIn(0f, (size.width - markWidth).coerceAtLeast(0f))
                val y = row * pitch + ((state ushr 14) and 3) * pixel * 0.20f
                if (x < clusterLeft || protected(x, y, markWidth, markHeight)) continue

                val alpha = when ((state ushr 24) and 3) {
                    0 -> 0.18f
                    1 -> 0.13f
                    else -> 0.085f
                }
                drawRect(
                    color = ink.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(markWidth, markHeight)
                )
            }
        }

        // A few segmented vertical runs descend from the unfinished lower edge.
        // Their lengths differ, and gaps keep them in the app's pixel language.
        for (column in 0 until columns) {
            state = fieldStep(state + column * 233)
            if ((state ushr 28) >= 3) continue
            val run = 3 + ((state ushr 7) and 7)
            val width = if (((state ushr 18) and 3) == 0) pixel * 2f else pixel
            val x = (column * pitch + pitch * 0.42f)
                .coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            for (step in 0 until run) {
                state = fieldStep(state + step * 97)
                if (step > 1 && ((state ushr 25) and 3) == 0) continue
                val height = if (step == run - 1) pixel * 2f else pixel
                val y = 45.dp.toPx() + step * pitch
                if (x < clusterLeft || y >= size.height || protected(x, y, width, height)) continue
                drawRect(
                    color = ink.copy(alpha = if (step == run - 1) 0.15f else 0.105f),
                    topLeft = Offset(x, y),
                    size = Size(width, height)
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
