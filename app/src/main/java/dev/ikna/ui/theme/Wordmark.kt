package dev.ikna.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.R
import kotlin.math.roundToInt

/*
 * The logo, inside the app, wearing the palette.
 *
 * This is the one mark in the app that is not drawn from scratch. Everything in
 * Flat.kt is geometry because a primitive reads as a set and a pulled-in icon
 * font does not; the wordmark is the opposite case. It is a typeface, set once,
 * and mipmap-anydpi-v26/ic_launcher.xml already states the rule this file obeys:
 * the letterforms are never traced into paths, because a wordmark redrawn as
 * outlines stops being the same wordmark. The letters here are the artwork's own
 * pixels, resampled, the same ones the launcher icon and the splash screen use.
 *
 * Two things had to be solved to let a bitmap live in a themed interface.
 *
 * The colour. The asset is pure white at varying alpha — the artwork is a single
 * cream tone, so its alpha channel *is* the letterform and the colour in the file
 * carries no information worth keeping. White plus alpha is the one form a bitmap
 * can take that tints exactly, so the wordmark can be ink on one palette and
 * near-black on another without shipping nine copies of it.
 *
 * The dot. The square over the i is the only part of the mark that is not a
 * letterform: it is a rectangle, so drawing it costs nothing and traces nothing.
 * It is erased from the asset and drawn here in the accent colour, which is what
 * makes the logo answer the palette instead of sitting on top of it. Twelve
 * palettes, twelve logos, one asset. Removing it from the bitmap rather than
 * painting over it matters — a leftover antialiased crumb under a hard-edged
 * square is exactly the kind of thing that is invisible on the screen it was
 * checked on and obvious on someone else's.
 *
 * The geometry below is measured from the artwork, not chosen. The dot is 85x88
 * source pixels in a 1090x444 box and sits in its very top left corner, which is
 * why two of the four numbers are zero. It is deliberately not squared off to
 * 85x85: the extra three pixels of height are in the original, and a mark that
 * disagrees with the launcher icon by three pixels is worse than one that carries
 * the original's own optical correction.
 */

/** Width over height of the whole mark, dot included. */
internal const val WORDMARK_ASPECT = 2.454955f

/** Where the square sits inside that box, as fractions of it. */
internal const val WORDMARK_DOT_LEFT = 0f
internal const val WORDMARK_DOT_TOP = 0f
internal const val WORDMARK_DOT_WIDTH = 0.077982f
internal const val WORDMARK_DOT_HEIGHT = 0.198198f

/**
 * The app's name as it is drawn everywhere else.
 *
 * Sized by [height], because the mark has one fixed proportion and a wordmark
 * given a width is a wordmark that will eventually be given the wrong one.
 *
 * Decorative by default. A screen reader that announces the app's own name on the
 * screen the app opens on is reading out furniture, and this mark is not
 * pressable: it is not a way back to anywhere, so there is nothing for a label to
 * promise. [label] exists for the one day it ends up somewhere it has to be read.
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
            painter = painterResource(R.drawable.ikna_wordmark),
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(ink)
        )
        // Rounded to whole pixels on purpose. Every corner in this app is a right
        // angle, and a rectangle landing on a fraction of a pixel is drawn with
        // two soft edges — at this size that is the difference between a square
        // and a smudge.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                color = dot,
                topLeft = Offset(
                    x = (size.width * WORDMARK_DOT_LEFT).roundToInt().toFloat(),
                    y = (size.height * WORDMARK_DOT_TOP).roundToInt().toFloat()
                ),
                size = Size(
                    width = (size.width * WORDMARK_DOT_WIDTH).roundToInt().coerceAtLeast(1).toFloat(),
                    height = (size.height * WORDMARK_DOT_HEIGHT).roundToInt().coerceAtLeast(1).toFloat()
                )
            )
        }
    }
}
