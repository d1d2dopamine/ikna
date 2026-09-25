package dev.ikna.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.ikna.shared.R
import java.io.File

actual fun iknaFontFamily(file: File): FontFamily = FontFamily(Font(file))
actual fun iknaGeologicaFontFamily(): FontFamily = FontFamily(
    Font(R.font.geologica_regular, weight = FontWeight.Normal),
    Font(R.font.geologica_medium, weight = FontWeight.Medium),
    Font(R.font.geologica_semibold, weight = FontWeight.SemiBold)
)
actual fun iknaPlexMonoMediumFontFamily(): FontFamily =
    FontFamily(Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium))
actual fun iknaPlexMonoSemiBoldFontFamily(): FontFamily =
    FontFamily(Font(R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold))

@Composable
actual fun iknaWordmarkPainter(): Painter = painterResource(R.drawable.ikna_wordmark)

@Composable
actual fun iknaWordmarkAccentPainter(): Painter = painterResource(R.drawable.ikna_wordmark_accent)
