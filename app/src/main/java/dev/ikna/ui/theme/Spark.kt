package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The app's own mark, drawn in the app.
 *
 * This is the same two-spark shape as the launcher icon, in the same proportions,
 * so the thing on the home screen and the thing at the end of a session are
 * recognisably one object. It replaces a Lottie file that played once, at the end
 * of a day, and cost an animation runtime plus a JSON asset to do it — a
 * dependency the size of the feature it served.
 *
 * Drawn rather than shipped also means it takes the palette. A user who sets a
 * custom accent gets their own colour here, instead of whatever colour was baked
 * into a JSON file by someone else.
 */

// Coordinates come straight from res/drawable/ic_launcher_foreground.xml, in the
// same 108-unit viewport, so the two can never drift apart.
private const val VIEWPORT = 108f

@Composable
fun IknaSpark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    /** 0 = not there, 1 = fully drawn. The small spark arrives second. */
    progress: Float = 1f
) {
    val shown = progress.coerceIn(0f, 1f)
    val second = ((shown - 0.45f) / 0.55f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = 0.72f + 0.28f * shown
                scaleY = 0.72f + 0.28f * shown
                alpha = shown
            }
    ) {
        val unit = this.size.minDimension / VIEWPORT
        fun u(value: Float) = value * unit

        val main = Path().apply {
            moveTo(u(50f), u(22f))
            quadraticBezierTo(u(54f), u(46f), u(71f), u(50f))
            quadraticBezierTo(u(54f), u(54f), u(50f), u(78f))
            quadraticBezierTo(u(46f), u(54f), u(29f), u(50f))
            quadraticBezierTo(u(46f), u(46f), u(50f), u(22f))
            close()
        }
        drawPath(main, color)

        if (second > 0.01f) {
            val small = Path().apply {
                moveTo(u(74f), u(63f))
                quadraticBezierTo(u(76f), u(70f), u(81f), u(72f))
                quadraticBezierTo(u(76f), u(74f), u(74f), u(81f))
                quadraticBezierTo(u(72f), u(74f), u(67f), u(72f))
                quadraticBezierTo(u(72f), u(70f), u(74f), u(63f))
                close()
            }
            drawPath(small, color.copy(alpha = color.alpha * second))
        }
    }
}
