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
 * Transparent masks for the in-app wordmark. Android loads drawable resources;
 * desktop loads the same PNGs from the classpath. Wordmark.kt applies the active
 * palette to them identically on both platforms.
 */
@Composable
expect fun iknaWordmarkPainter(): Painter

@Composable
expect fun iknaWordmarkAccentPainter(): Painter
