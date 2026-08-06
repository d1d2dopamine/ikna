package dev.ikna.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode

/*
 * Paper and ink, one accent, right angles.
 *
 * What this deliberately is not: a Material 3 default. No dynamic colour from
 * the wallpaper, no rounded corners, no elevation tints, no green/red pair on
 * the answer buttons. Punishment colours are a bad idea for anyone and a worse
 * one here.
 *
 * A theme is exactly four colours — background, ink, muted, accent — and
 * everything else is derived from them. That is not minimalism for its own sake:
 * it is what makes a user-defined theme possible at all. The old file also kept
 * four colours as top-level constants shared by both schemes, which is why the
 * light theme came out brown: a muted warm grey that works on near-black is a
 * brown smudge on paper, and it was the same value in both.
 */

// ---- light: actually light -------------------------------------------------
//
// The previous "light" theme was #F1EFE9 paper with a #6A6454 brown for
// secondary text — a beige scheme with a navy accent, which reads as an inverted
// dark theme rather than as a light one. This is near-white and hue-neutral.
private val LightBackground = Color(0xFFFBFAF8)
private val LightInk = Color(0xFF2C2A27)
private val LightMuted = Color(0xFF6B685F)
private val LightAccent = Color(0xFF33469E)

// ---- dark ------------------------------------------------------------------
private val DarkBackground = Color(0xFF121110)
private val DarkInk = Color(0xFFEDE9E1)
private val DarkMuted = Color(0xFF8F887A)
private val DarkAccent = Color(0xFF97A4D8)

/**
 * Reserved for destructive controls only: wiping data, starting over.
 *
 * Deliberately not part of [IknaPalette]. "This one is dangerous" has to survive
 * any colour scheme the user invents, including one where the accent is red.
 */
val IknaDanger = Color(0xFFB44A34)

/**
 * A whole theme.
 *
 * Four colours in, everything else out. Panel and line are mixed from the first
 * two rather than picked, so a custom theme cannot end up with a panel that is
 * invisible against its own background.
 */
data class IknaPalette(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color
) {
    /** Surfaces that need to be a step away from the background. */
    val panel: Color get() = mix(background, ink, 0.07f)

    /** Borders and rules. */
    val line: Color get() = mix(background, ink, 0.28f)

    /** Drives the colour scheme choice and the system bar icons. */
    val light: Boolean get() = isLight(background)
}

val DarkPalette = IknaPalette(
    background = DarkBackground,
    ink = DarkInk,
    muted = DarkMuted,
    accent = DarkAccent
)

val LightPalette = IknaPalette(
    background = LightBackground,
    ink = LightInk,
    muted = LightMuted,
    accent = LightAccent
)

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)

/** The user's four colours, as stored. */
fun customPaletteOf(settings: IknaSettings): IknaPalette = IknaPalette(
    background = Color(settings.customBackground),
    ink = Color(settings.customInk),
    muted = Color(settings.customMuted),
    accent = Color(settings.customAccent)
)

/** Which palette a mode resolves to. Used by the theme and by the system bars. */
fun paletteFor(settings: IknaSettings): IknaPalette = when (settings.theme) {
    ThemeMode.DARK -> DarkPalette
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.CUSTOM -> customPaletteOf(settings)
}

/**
 * Every Material slot the app actually reads, filled from four colours.
 *
 * onSurfaceVariant is the muted colour and outline is the line colour, so a
 * screen never has to import a constant to draw secondary text — which is what
 * made the old palette impossible to theme.
 */
private fun schemeOf(p: IknaPalette) = if (p.light) {
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.background,
        secondary = p.accent,
        onSecondary = p.background,
        background = p.background,
        onBackground = p.ink,
        surface = p.panel,
        onSurface = p.ink,
        surfaceVariant = p.panel,
        onSurfaceVariant = p.muted,
        outline = p.line,
        outlineVariant = p.line,
        error = IknaDanger,
        onError = p.background
    )
} else {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.background,
        secondary = p.accent,
        onSecondary = p.background,
        background = p.background,
        onBackground = p.ink,
        surface = p.panel,
        onSurface = p.ink,
        surfaceVariant = p.panel,
        onSurfaceVariant = p.muted,
        outline = p.line,
        outlineVariant = p.line,
        error = IknaDanger,
        onError = p.background
    )
}

/**
 * Two poles and nothing in between: content is large, service text is small and
 * monospaced.
 *
 * Weights come down a step from the previous version. Display text was
 * ExtraBold at 50sp in near-black, which is not emphasis, it is a stain — the
 * size already carries the emphasis and the weight was only adding ink.
 *
 * Nothing here names a font family except the labels, because no font file ships
 * with the app yet and naming one that does not exist fails the build. Content
 * therefore renders in the platform sans, which is the one honest placeholder
 * until the bundled faces are chosen.
 */
private val Mono = FontFamily.Monospace

private val IknaTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 46.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.0).sp
    ),
    displayMedium = TextStyle(
        fontSize = 38.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp
    ),
    displaySmall = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
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
    palette: IknaPalette = DarkPalette,
    content: @Composable () -> Unit
) {
    val scheme = schemeOf(palette)
    MaterialTheme(
        colorScheme = scheme,
        typography = IknaTypography,
        shapes = IknaShapes
    ) {
        // Two defaults were quietly overriding the whole palette.
        //
        // Nothing in the tree ever painted the window. There is no Surface
        // anywhere — deliberately, it is a Material 3 component — so what showed
        // through was the Android window background from Theme.Material, a warm
        // dark grey. The light theme was computed correctly and then never
        // reached the screen: what the eye got was that grey plus the blue
        // accent, which is exactly "brown with blue".
        //
        // And LocalContentColor falls back to black when no Surface provides it,
        // so every Text that did not name a colour — the screen titles, most of
        // all — rendered black on both themes.
        //
        // Both are fixed here once, for every screen, instead of per call site.
        CompositionLocalProvider(LocalContentColor provides palette.ink) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.background)
            ) {
                content()
            }
        }
    }
}
