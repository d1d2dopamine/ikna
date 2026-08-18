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
 * Empty space with a memory in it.
 *
 * The field is deliberately sparse and quiet. It is not wallpaper: most of the
 * screen remains untouched, while a few cells and interrupted routes make a
 * large unused area read as reserved space rather than missing content. The
 * card screen never uses this component; silence around a phrase is functional
 * and must stay completely silent.
 */

/** A low-contrast, deterministic lattice for genuinely empty screen regions. */
@Composable
fun IknaMemoryField(
    seed: Int,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier.fillMaxSize()) {
        val step = 24.dp.toPx()
        val cell = 4.dp.toPx()
        val columns = (size.width / step).toInt().coerceAtLeast(1)
        val rows = (size.height / step).toInt().coerceAtLeast(1)
        val firstRow = (rows * 0.42f).toInt().coerceAtMost(rows - 1)
        val availableRows = (rows - firstRow).coerceAtLeast(1)
        val marks = minOf(30, (columns * availableRows / 5).coerceAtLeast(8))
        var state = seed xor 0x4B1D5A77

        repeat(marks) { index ->
            state = fieldStep(state + index * 31)
            val column = (state ushr 1) % columns
            state = fieldStep(state)
            val row = firstRow + (state ushr 1) % availableRows
            // A few cells are short vertical fragments. This is the same
            // distinction as the activity map: a cell can be present without
            // pretending it is complete.
            val height = if ((state and 3) == 0) cell * 2f else cell
            drawRect(
                color = ink.copy(alpha = if ((state and 7) == 0) 0.10f else 0.055f),
                topLeft = Offset(column * step, row * step),
                size = Size(cell, height)
            )
        }

        // Two interrupted routes, never a frame and never a full-width rule.
        // They give the scattered cells a direction without competing with the
        // actual separators and controls above them.
        val route = ink.copy(alpha = 0.045f)
        val y1 = size.height * 0.68f
        val y2 = size.height * 0.82f
        drawLine(route, Offset(size.width * 0.58f, y1), Offset(size.width * 0.78f, y1), 1.dp.toPx())
        drawLine(route, Offset(size.width * 0.78f, y1), Offset(size.width * 0.78f, y2), 1.dp.toPx())
        drawLine(route, Offset(size.width * 0.78f, y2), Offset(size.width * 0.94f, y2), 1.dp.toPx())
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
