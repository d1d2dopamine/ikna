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

/** A quiet, opaque tonal field drawn within the deck destination. */
@Composable
fun IknaHomeBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to lerp(colors.background, colors.surface, 0.48f),
                0.55f to colors.background,
                1f to lerp(colors.background, colors.surfaceVariant, 0.16f)
            )
        )
    )
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
