package dev.ikna.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.ThemeMode

private val Amber = Color(0xFFE8C547)
private val Ink = Color(0xFF12100E)
private val Surface = Color(0xFF1C1A17)
private val Muted = Color(0xFF8A8477)
private val Good = Color(0xFF62B87A)
private val Again = Color(0xFFD1584F)

val IknaGood = Good
val IknaAgain = Again
val IknaMuted = Muted

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color(0xFFF2EFE7),
    surface = Surface,
    onSurface = Color(0xFFF2EFE7),
    surfaceVariant = Color(0xFF2A2723),
    onSurfaceVariant = Muted,
    error = Again
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6D0B),
    background = Color(0xFFFBF8F1),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1A16),
    surfaceVariant = Color(0xFFF0EBDD),
    onSurfaceVariant = Color(0xFF5F5A4E),
    error = Again
)

private val IknaTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

/**
 * Theme is a preference now, not a guess.
 *
 * Dynamic colour is on by default because a familiar-looking app is one less
 * thing that feels like a separate task, but it can be turned off for people who
 * find the system palette too loud.
 */
@Composable
fun IknaTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = IknaTypography,
        content = content
    )
}
