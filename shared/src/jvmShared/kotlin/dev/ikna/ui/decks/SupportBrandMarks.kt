/*
 * GitHub artwork from GitHub's Primer Octicons at revision e6626e159c4ba786d512e252182b62617014c752.
 * Gmail artwork from Google's Workspace brand assets (https://knowledge.workspace.google.com/static/images/icons/product/gmail.svg).
 * The Gmail logo is a trademark of Google LLC.
 *
 * Octicons license:
 * MIT License
 *
 * Copyright (c) 2026 GitHub Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.ikna.ui.decks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaGlyphIcon

internal enum class SupportBrandIcon { REPOSITORY, ISSUE, GMAIL, AUTHOR, WEBSITE }

@Composable
internal fun SupportBrandMark(icon: SupportBrandIcon) {
    if (icon == SupportBrandIcon.GMAIL) {
        GmailMark()
        return
    }
    val colors = MaterialTheme.colorScheme
    val ink = if (colors.surfaceVariant.luminance() > 0.179f) Color.Black else Color.White
    if (icon == SupportBrandIcon.WEBSITE) {
        IknaGlyphIcon(glyph = IknaGlyph.GLOBE, color = ink, size = 24.dp)
        return
    }
    val outline = remember(icon) {
        val data = when (icon) {
            SupportBrandIcon.REPOSITORY -> githubRepoPath
            SupportBrandIcon.ISSUE -> githubIssuePath
            SupportBrandIcon.AUTHOR -> githubAuthorPath
            SupportBrandIcon.GMAIL, SupportBrandIcon.WEBSITE -> error("Handled above")
        }
        PathParser().parsePathString(data).toPath()
    }
    Canvas(Modifier.size(24.dp)) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) { drawPath(outline, ink) }
    }
}

@Composable
private fun GmailMark() {
    val shapes = remember {
        listOf(gmailRightPath, gmailLeftPath, gmailFoldPath)
            .map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(Modifier.size(24.dp)) {
        scale(size.minDimension / 192f, pivot = Offset.Zero) {
            drawPath(shapes[0], gmailRightColors)
            drawPath(shapes[1], Color(0xFFFC413D))
            drawPath(shapes[2], gmailFoldColors)
        }
    }
}

private const val githubRepoPath = "M10.226 17.284c-2.965-.36-5.054-2.493-5.054-5.256 0-1.123.404-2.336 1.078-3.144-.292-.741-.247-2.314.09-2.965.898-.112 2.111.36 2.83 1.01.853-.269 1.752-.404 2.853-.404 1.1 0 1.999.135 2.807.382.696-.629 1.932-1.1 2.83-.988.315.606.36 2.179.067 2.942.72.854 1.101 2 1.101 3.167 0 2.763-2.089 4.852-5.098 5.234.763.494 1.28 1.572 1.28 2.807v2.336c0 .674.561 1.056 1.235.786 4.066-1.55 7.255-5.615 7.255-10.646C23.5 6.188 18.334 1 11.978 1 5.62 1 .5 6.188.5 12.545c0 4.986 3.167 9.12 7.435 10.669.606.225 1.19-.18 1.19-.786V20.63a2.9 2.9 0 0 1-1.078.224c-1.483 0-2.359-.808-2.987-2.313-.247-.607-.517-.966-1.034-1.033-.27-.023-.359-.135-.359-.27 0-.27.45-.471.898-.471.652 0 1.213.404 1.797 1.235.45.651.921.943 1.483.943.561 0 .92-.202 1.437-.719.382-.381.674-.718.944-.943"
private const val githubIssuePath = "M12 1c6.075 0 11 4.925 11 11s-4.925 11-11 11S1 18.075 1 12 5.925 1 12 1ZM2.5 12a9.5 9.5 0 0 0 9.5 9.5 9.5 9.5 0 0 0 9.5-9.5A9.5 9.5 0 0 0 12 2.5 9.5 9.5 0 0 0 2.5 12Zm9.5 2a2 2 0 1 1-.001-3.999A2 2 0 0 1 12 14Z"
private const val githubAuthorPath = "M12 2.5a5.5 5.5 0 0 1 3.096 10.047 9.005 9.005 0 0 1 5.9 8.181.75.75 0 1 1-1.499.044 7.5 7.5 0 0 0-14.993 0 .75.75 0 0 1-1.5-.045 9.005 9.005 0 0 1 5.9-8.18A5.5 5.5 0 0 1 12 2.5ZM8 8a4 4 0 1 0 8 0 4 4 0 0 0-8 0Z"

private const val gmailRightPath = "M146 44h38v110c0 6.627-5.373 12-12 12h-20a6 6 0 0 1-6-6z"
private const val gmailLeftPath = "M46 44H8v110c0 6.627 5.373 12 12 12h20a6 6 0 0 0 6-6z"
private const val gmailFoldPath = "M39.226 30.456c-8.033-6.752-20.018-5.714-26.77 2.319-6.752 8.032-5.714 20.017 2.319 26.77l76.078 63.949a8 8 0 0 0 10.295 0l76.078-63.95c8.032-6.752 9.07-18.737 2.318-26.77-6.752-8.032-18.737-9.07-26.769-2.318L96 78.18z"

private val gmailRightColors = Brush.linearGradient(
    0.0f to Color(0xFF60D673),
    0.17f to Color(0xFF42C868),
    0.39f to Color(0xFF0EBC5F),
    0.62f to Color(0xFF00A9BB),
    0.86f to Color(0xFF3C90FF),
    1.0f to Color(0xFF3186FF),
    start = Offset(165.0f, 44.0f),
    end = Offset(165.0f, 166.0f)
)

private val gmailFoldColors = Brush.linearGradient(
    0.08f to Color(0xFFFF63A0),
    0.3f to Color(0xFFFC413D),
    0.5f to Color(0xFFFC413D),
    0.65f to Color(0xFFFC413D),
    0.72f to Color(0xFFFC5C30),
    0.86f to Color(0xFFFEB10C),
    0.91f to Color(0xFFFEC700),
    0.96f to Color(0xFFFFDB0F),
    start = Offset(8.0f, 46.13f),
    end = Offset(184.0f, 46.13f)
)
