package dev.ikna.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import dev.ikna.shared.R
import java.io.File

actual fun iknaFontFamily(file: File): FontFamily = FontFamily(Font(file))

@Composable
actual fun iknaWordmarkPainter(): Painter = painterResource(R.drawable.ikna_wordmark)
