package dev.ikna.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The logo, inside the app, wearing the palette.
 *
 * The supplied 0.11 wordmark is raster artwork. The black presentation field is
 * not part of the in-app mark, so the artwork is stored here as two transparent
 * alpha masks: the blue letterforms and the warm cap above the i. Keeping the two
 * masks separate lets Compose tint both from the active palette without tracing,
 * retyping or otherwise redrawing the logo.
 *
 * The letter mask receives the interface ink colour. The cap receives the
 * palette accent. That preserves the behaviour the previous wordmark had: change
 * the selected palette and the brand mark changes with it, on Android and on the
 * desktop, while the supplied geometry stays untouched.
 */

/** Width over height of the supplied mark after its black presentation field is removed. */
const val WORDMARK_ASPECT = 2.928166f

/**
 * The app's name as it is drawn inside the interface.
 *
 * Sized by [height], because the mark has one fixed proportion. Decorative by
 * default; [label] exists for the places where the mark itself needs to be read.
 *
 * [ink] and [dot] deliberately stay independent. The parameter is still named
 * `dot` for source compatibility with the desktop shell, although the new accent
 * is the rounded cap from the supplied 0.11 artwork rather than the old square.
 */
@Composable
fun IknaWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 18.dp,
    ink: Color = MaterialTheme.colorScheme.onBackground,
    dot: Color = MaterialTheme.colorScheme.primary,
    label: String? = null
) {
    Box(modifier = modifier.height(height).width(height * WORDMARK_ASPECT)) {
        Image(
            painter = iknaWordmarkPainter(),
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(ink)
        )
        Image(
            painter = iknaWordmarkAccentPainter(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(dot)
        )
    }
}
