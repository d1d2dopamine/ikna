package dev.ikna.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp

/**
 * Decorative reading cat. The transparent raster uses black contours, red book
 * and white interiors. Map those three roles to ink, accent and the current
 * background without changing its alpha, so no white patch remains in any theme.
 * It occupies its own header slot; it never overlays the title or Today block.
 */
internal fun readingCatColorMatrix(ink: Color, accent: Color, paper: Color): ColorMatrix =
    ColorMatrix(floatArrayOf(
        accent.red - ink.red, paper.red - accent.red, 0f, 0f, ink.red * 255f,
        accent.green - ink.green, paper.green - accent.green, 0f, 0f, ink.green * 255f,
        accent.blue - ink.blue, paper.blue - accent.blue, 0f, 0f, ink.blue * 255f,
        0f, 0f, 0f, 1f, 0f
    ))

@Composable
fun IknaReadingCat(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onBackground
    val accent = scheme.primary
    val paper = scheme.background
    val filter = remember(ink, accent, paper) {
        ColorFilter.colorMatrix(readingCatColorMatrix(ink, accent, paper))
    }
    Image(
        painter = iknaReadingCatPainter(),
        contentDescription = null,
        modifier = modifier.size(48.dp),
        colorFilter = filter
    )
}
