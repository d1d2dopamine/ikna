package dev.ikna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.ThemeMode

/*
 * Paper and ink, one accent, right angles.
 *
 * What this deliberately is not: a Material 3 default. No dynamic colour from
 * the wallpaper (the palette used to change per phone, which is why it never
 * looked designed), no rounded corners, no elevation tints, no green/red pair on
 * the answer buttons. Punishment colours are a bad idea for anyone and a worse
 * one here.
 */

private val Paper = Color(0xFFF0EBDF)
private val PaperPanel = Color(0xFFE6E0D0)
private val PaperLine = Color(0xFFC9C1AE)
private val InkOnPaper = Color(0xFF15130F)
private val MutedOnPaper = Color(0xFF6A6454)
private val AccentOnPaper = Color(0xFF2340E0)

private val Ink = Color(0xFF100E0B)
private val InkPanel = Color(0xFF1B1813)
private val InkLine = Color(0xFF322D25)
private val PaperOnInk = Color(0xFFEFEADE)
private val MutedOnInk = Color(0xFF8C8574)
private val AccentOnInk = Color(0xFF7C90FF)

/**
 * Mid-tone warm grey that reads as secondary on both schemes, so screens can use
 * one constant instead of branching on the theme.
 */
val IknaMuted = Color(0xFF8A8474)

/** Line weight colour for borders and rules, readable on both schemes. */
val IknaLine = Color(0xFF7A7466)

/**
 * Swipe tints. "Remembered" is the accent, "forgot" is warm graphite — not red.
 * Forgetting is the normal half of the method and must not look like a mistake.
 */
val IknaGood = Color(0xFF3350E8)
val IknaAgain = Color(0xFF6E6656)

/** Reserved for destructive controls only: wiping data, starting over. */
val IknaDanger = Color(0xFFB44A34)

private val DarkColors = darkColorScheme(
    primary = AccentOnInk,
    onPrimary = Ink,
    secondary = AccentOnInk,
    background = Ink,
    onBackground = PaperOnInk,
    surface = InkPanel,
    onSurface = PaperOnInk,
    surfaceVariant = InkPanel,
    onSurfaceVariant = MutedOnInk,
    outline = InkLine,
    outlineVariant = InkLine,
    error = IknaDanger,
    onError = PaperOnInk
)

private val LightColors = lightColorScheme(
    primary = AccentOnPaper,
    onPrimary = Paper,
    secondary = AccentOnPaper,
    background = Paper,
    onBackground = InkOnPaper,
    surface = PaperPanel,
    onSurface = InkOnPaper,
    surfaceVariant = PaperPanel,
    onSurfaceVariant = MutedOnPaper,
    outline = PaperLine,
    outlineVariant = PaperLine,
    error = IknaDanger,
    onError = Paper
)

/**
 * Two poles and nothing in between: content is huge, service text is tiny and
 * monospaced. The gap is what gives the screen a rhythm — the old version set
 * everything two steps apart, which is why it read as flat and dull at once.
 *
 * Monospace for labels is a free way to look intentional without shipping a font
 * file, and it is the same family Material uses for nothing, so it reads as ours.
 */
private val Mono = FontFamily.Monospace

private val IknaTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 50.sp,
        lineHeight = 54.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontSize = 40.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontFamily = Mono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp
    )
)

/** Every corner in the app is a right angle. This is the whole shape system. */
private val Square = RoundedCornerShape(0.dp)

private val IknaShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square
)

@Composable
fun IknaTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = IknaTypography,
        shapes = IknaShapes,
        content = content
    )
}
