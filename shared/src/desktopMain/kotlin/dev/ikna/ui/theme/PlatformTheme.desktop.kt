package dev.ikna.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import java.io.File
import javax.imageio.ImageIO

actual fun iknaFontFamily(file: File): FontFamily = FontFamily(Font(file))

private object WordmarkAnchor

/** Drawn as nothing rather than crashing: a missing logo is not worth a window. */
private object NothingPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
}

private fun loadWordmark(): Painter {
    val stream = WordmarkAnchor.javaClass.getResourceAsStream("/drawable/ikna_wordmark.png")
        ?: return NothingPainter
    val image = runCatching { stream.use { ImageIO.read(it) } }.getOrNull() ?: return NothingPainter
    return BitmapPainter(image.toComposeImageBitmap())
}

@Composable
actual fun iknaWordmarkPainter(): Painter = remember { loadWordmark() }
