package dev.ikna.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Loading a font file from disk. Android and the desktop both do it, with the
 * same argument and the same result, from two different packages.
 */
expect fun iknaFontFamily(file: File): FontFamily

/**
 * The wordmark bitmap. A drawable resource on Android, a classpath resource on
 * the desktop; Wordmark.kt tints and draws it identically either way.
 */
@Composable
expect fun iknaWordmarkPainter(): Painter
