package dev.ikna.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

actual fun iknaFontFamily(file: File): FontFamily = FontFamily(Font(file))

private object WordmarkAnchor

private fun bundledFontFile(resource: String): File {
    val bytes = WordmarkAnchor.javaClass.getResourceAsStream(resource)?.use { it.readBytes() }
        ?: error("Missing bundled font resource: $resource")
    val cache = File(System.getProperty("java.io.tmpdir"), "ikna-bundled-fonts").apply { mkdirs() }
    val stem = resource.substringAfterLast('/').substringBeforeLast('.')
    val hash = bytes.contentHashCode().toUInt().toString(16)
    val file = File(cache, "$stem-$hash.ttf")
    if (!file.exists()) file.writeBytes(bytes)
    file.deleteOnExit()
    return file
}

actual fun iknaGeologicaFontFamily(): FontFamily = FontFamily(
    Font(bundledFontFile("/font/geologica_regular.ttf"), weight = FontWeight.Normal),
    Font(bundledFontFile("/font/geologica_medium.ttf"), weight = FontWeight.Medium),
    Font(bundledFontFile("/font/geologica_semibold.ttf"), weight = FontWeight.SemiBold)
)

actual fun iknaPlexMonoMediumFontFamily(): FontFamily =
    FontFamily(Font(bundledFontFile("/font/ibm_plex_mono_medium.ttf"), weight = FontWeight.Medium))

actual fun iknaPlexMonoSemiBoldFontFamily(): FontFamily =
    FontFamily(Font(bundledFontFile("/font/ibm_plex_mono_semibold.ttf"), weight = FontWeight.SemiBold))

/** Drawn as nothing rather than crashing: a missing logo is not worth a window. */
private object NothingPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified
    override fun DrawScope.onDraw() = Unit
}

private fun loadWordmark(resource: String, filterQuality: FilterQuality = FilterQuality.Low): Painter {
    val stream = WordmarkAnchor.javaClass.getResourceAsStream(resource) ?: return NothingPainter
    val image = runCatching { stream.use { ImageIO.read(it) } }.getOrNull() ?: return NothingPainter
    return BitmapPainter(image.toComposeImageBitmap(), filterQuality = filterQuality)
}

@Composable
actual fun iknaWordmarkPainter(): Painter = remember {
    loadWordmark("/drawable/ikna_wordmark.png")
}

@Composable
actual fun iknaWordmarkAccentPainter(): Painter = remember {
    loadWordmark("/drawable/ikna_wordmark_accent.png")
}

private fun downscaleSmooth(source: BufferedImage, targetSize: Int): BufferedImage {
    var current = source
    while (current.width > targetSize * 2 && current.height > targetSize * 2) {
        val nextW = (current.width / 2).coerceAtLeast(targetSize)
        val nextH = (current.height / 2).coerceAtLeast(targetSize)
        val step = BufferedImage(nextW, nextH, BufferedImage.TYPE_INT_ARGB)
        val g = step.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(current, 0, 0, nextW, nextH, null)
        g.dispose()
        current = step
    }
    if (current.width <= targetSize && current.height <= targetSize) return current
    val scale = targetSize.toDouble() / maxOf(current.width, current.height).toDouble()
    val outW = (current.width * scale).toInt().coerceAtLeast(1)
    val outH = (current.height * scale).toInt().coerceAtLeast(1)
    val out = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.drawImage(current, 0, 0, outW, outH, null)
    g.dispose()
    return out
}

@Composable
actual fun iknaReadingCatPainter(): Painter = remember {
    val stream = WordmarkAnchor.javaClass.getResourceAsStream("/drawable/ikna_reading_cat.png")
    val image = stream?.let { runCatching { it.use { s -> ImageIO.read(s) } }.getOrNull() }
    if (image == null) {
        NothingPainter
    } else {
        BitmapPainter(downscaleSmooth(image, 192).toComposeImageBitmap(), filterQuality = FilterQuality.Medium)
    }
}
