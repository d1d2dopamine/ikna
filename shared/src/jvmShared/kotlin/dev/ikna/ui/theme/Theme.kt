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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.DEFAULT_PALETTE_ID
import dev.ikna.data.prefs.FontStore
import dev.ikna.data.prefs.FontMode
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import kotlin.math.abs
import kotlin.math.min

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

// ---- danger ----------------------------------------------------------------
//
// Reserved for destructive controls only: wiping data, starting over.
//
// Deliberately not part of [IknaPalette] — "this one is dangerous" has to
// survive any colour scheme, including one the user invented. But it cannot be a
// single constant either, and the old one proved it twice over:
//
// #B44A34 measured 3.5:1 on every dark background the app ships, which is below
// the line for text and was the least readable colour in the product — on the one
// control where a misread is unrecoverable. So there are two, one per lighting.
//
// And a red warning stops being a warning next to a warm accent: on the default
// palette the ember accent and any red are the same colour to a glance, so
// "стереть всё" would look like an ordinary button. When the accent is that
// close in hue, danger is carried by the word and the frame instead, and the
// colour steps back to ink. Colour was never allowed to be the only carrier of
// meaning anyway.
private val DangerOnDark = Color(0xFFFF7A66)
private val DangerOnLight = Color(0xFF962A17)

private const val DANGER_HUE = 8f
private const val DANGER_HUE_GUARD = 26f
private const val DANGER_MIN_SATURATION = 0.35f

/** The colour of a destructive control under this palette. */
fun dangerFor(palette: IknaPalette): Color {
    if (clashesWithDanger(palette.accent)) return palette.ink
    return if (palette.light) DangerOnLight else DangerOnDark
}

/** True when the accent is close enough to the danger red to be mistaken for it. */
fun clashesWithDanger(accent: Color): Boolean {
    val (hue, saturation) = hueAndSaturation(accent)
    if (saturation < DANGER_MIN_SATURATION) return false
    val distance = abs(hue - DANGER_HUE)
    return min(distance, 360f - distance) < DANGER_HUE_GUARD
}

/**
 * Hue in degrees and saturation, computed rather than taken from the platform:
 * android.graphics.Color is not available in a unit test, and this rule is
 * asserted by one.
 */
fun hueAndSaturation(color: Color): Pair<Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val high = maxOf(r, g, b)
    val low = minOf(r, g, b)
    val span = high - low
    if (span <= 0f || high <= 0f) return 0f to 0f
    val raw = when (high) {
        r -> 60f * ((g - b) / span)
        g -> 60f * ((b - r) / span + 2f)
        else -> 60f * ((r - g) / span + 4f)
    }
    val hue = ((raw % 360f) + 360f) % 360f
    return hue to (span / high)
}

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

/**
 * A palette and both of its lightings.
 *
 * The pair is authored by hand, not derived. A light theme computed by inverting
 * a dark one is how the previous light theme came out brown: a warm muted grey
 * that works on near-black is a smudge on paper, and a saturated accent that
 * glows on a dark field is unreadable on a bright one — the ember accent measures
 * 6.1:1 on its own dark background and would be about 2:1 on paper. Every light
 * accent below is therefore a darker, deeper version of the same hue, not the
 * same value reused.
 *
 * The name is a key in the string catalogue, so palettes are translated like
 * everything else in the interface.
 */
data class IknaPaletteSpec(
    val id: String,
    val nameKey: String,
    val dark: IknaPalette,
    val light: IknaPalette
) {
    fun palette(light: Boolean): IknaPalette = if (light) this.light else this.dark
}

/**
 * Every palette the app ships, in the order they are offered.
 *
 * Each one is a hue in the background with grey paper of the same hue as its light
 * version — one brand in two lightings. "Ноль" is the exception on purpose: pure
 * black in dark lighting and neutral grey in light lighting. Its recognisable
 * part is the absence of hue, not an obligation to draw blinding white fills.
 *
 * Every entry here is asserted readable, in both lightings, by PaletteTest.
 */
val IknaPalettes: List<IknaPaletteSpec> = listOf(
    // Чернила. The clean-install default: ink-blue paper with a warm accent on it.
    IknaPaletteSpec(
        id = "ink",
        nameKey = "set.118",
        dark = IknaPalette(
            background = Color(0xFF0B1120),
            ink = Color(0xFFE5EAF4),
            muted = Color(0xFF78859C),
            accent = Color(0xFFFF7A5C)
        ),
        light = IknaPalette(
            background = Color(0xFFD0D3D9),
            ink = Color(0xFF0E1526),
            muted = Color(0xFF485162),
            accent = Color(0xFF932D19)
        )
    ),
    // Библиотека. Bottle green and brass: a reading room, nothing hurrying.
    IknaPaletteSpec(
        id = "library",
        nameKey = "set.117",
        dark = IknaPalette(
            background = Color(0xFF0F1712),
            ink = Color(0xFFE8EEE4),
            muted = Color(0xFF7D9083),
            accent = Color(0xFFE3B45E)
        ),
        light = IknaPalette(
            background = Color(0xFFD1D4CD),
            ink = Color(0xFF14201A),
            muted = Color(0xFF49534C),
            accent = Color(0xFF644E0F)
        )
    ),
    // Уголь. Kept for existing installs: burnt earth and an ember, finally
    // also inside the app.
    IknaPaletteSpec(
        id = "ember",
        nameKey = "set.116",
        dark = IknaPalette(
            background = Color(0xFF17100C),
            ink = Color(0xFFF2E6D9),
            muted = Color(0xFF9C8574),
            accent = Color(0xFFF2683C)
        ),
        light = IknaPalette(
            background = Color(0xFFD6D0C9),
            ink = Color(0xFF241610),
            muted = Color(0xFF5E4C41),
            accent = Color(0xFF8E3016)
        )
    ),
    // Слива. The loudest one: aubergine and mint.
    IknaPaletteSpec(
        id = "plum",
        nameKey = "set.119",
        dark = IknaPalette(
            background = Color(0xFF150F1C),
            ink = Color(0xFFEDE6F1),
            muted = Color(0xFF897D94),
            accent = Color(0xFF45D6A6)
        ),
        light = IknaPalette(
            background = Color(0xFFD3CFD7),
            ink = Color(0xFF1D1324),
            muted = Color(0xFF554B5E),
            accent = Color(0xFF085A41)
        )
    ),
    // Роза. Pink without the sugar: a wine-dark field and a rose that reads as a
    // highlighter, not a ribbon.
    //
    // The accent sits at hue 333 deliberately. Twenty-five degrees further round
    // the wheel is the danger guard, and an accent that trips it hands the warning
    // colour back to the ink — a palette with one colour less. Pink is the hue that
    // gets closest to that line without crossing it, which is the only reason these
    // particular pinks and not the obvious ones.
    IknaPaletteSpec(
        id = "rose",
        nameKey = "set.125",
        dark = IknaPalette(
            background = Color(0xFF1A0E15),
            ink = Color(0xFFF6E7EE),
            muted = Color(0xFFA2808F),
            accent = Color(0xFFFF7FB8)
        ),
        light = IknaPalette(
            background = Color(0xFFD7CED2),
            ink = Color(0xFF26121C),
            muted = Color(0xFF654653),
            accent = Color(0xFF931A50)
        )
    ),
    // Иней. The one palette with nothing warm in it anywhere.
    //
    // Every other scheme here answers a cool background with a warm accent, because
    // that is the safe move. This one refuses: cold field, cold ink, cold accent.
    // Чернила is navy paper with a coral on it and is not this; the difference is
    // ninety degrees of hue on the one colour that carries the day's number.
    IknaPaletteSpec(
        id = "frost",
        nameKey = "set.126",
        dark = IknaPalette(
            background = Color(0xFF08131A),
            ink = Color(0xFFE2EEF3),
            muted = Color(0xFF7C929C),
            accent = Color(0xFF5FD2E8)
        ),
        light = IknaPalette(
            background = Color(0xFFCDD4D7),
            ink = Color(0xFF0C1B22),
            muted = Color(0xFF41535B),
            accent = Color(0xFF095364)
        )
    ),
    // Фосфор. The odd one out, and the reason it is here.
    //
    // In the other coloured palettes, the ink is a neutral and the accent is the only coloured
    // thing on the screen. Here the ink itself is the colour, so the whole surface
    // is one hue and the accent is merely a brighter pass of it — a phosphor tube,
    // where the glass could only ever glow in one direction.
    //
    // It is not Библиотека with the lights off. That one is a green room with a
    // brass lamp in it; this one has no second colour to fall back on, and the
    // light version keeps the conceit by putting the same green into the ink rather
    // than inverting to a neutral black.
    IknaPaletteSpec(
        id = "phosphor",
        nameKey = "set.127",
        dark = IknaPalette(
            background = Color(0xFF040A06),
            ink = Color(0xFFB8F5CB),
            muted = Color(0xFF6DA981),
            accent = Color(0xFF4AF08C)
        ),
        light = IknaPalette(
            background = Color(0xFFCFD6D0),
            ink = Color(0xFF0A1E11),
            muted = Color(0xFF405646),
            accent = Color(0xFF0D5B2D)
        )
    ),
    // Ноль. No hue at all, in either direction.
    IknaPaletteSpec(
        id = "zero",
        nameKey = "set.120",
        dark = IknaPalette(
            background = Color(0xFF000000),
            ink = Color(0xFFFFFFFF),
            muted = Color(0xFF8F8F8F),
            accent = Color(0xFFFFFFFF)
        ),
        light = IknaPalette(
            background = Color(0xFFD2D2D2),
            ink = Color(0xFF242424),
            muted = Color(0xFF505050),
            accent = Color(0xFF343434)
        )
    ),
    // The original neutral identity, now with the same grey light treatment.
    // Existing palette ids and user choices do not change.
    IknaPaletteSpec(
        id = "neutral",
        nameKey = "set.121",
        dark = IknaPalette(
            background = Color(0xFF121110),
            ink = Color(0xFFEDE9E1),
            muted = Color(0xFF8F887A),
            accent = Color(0xFF97A4D8)
        ),
        light = IknaPalette(
            background = Color(0xFFD4D2CE),
            ink = Color(0xFF2C2A27),
            muted = Color(0xFF53514B),
            accent = Color(0xFF2E3D89)
        )
    ),
    // Ультрафиолет. Purple is the accent as well as the atmosphere: unlike
    // Слива, this one does not answer an aubergine field with mint.
    IknaPaletteSpec(
        id = "ultraviolet",
        nameKey = "set.141",
        dark = IknaPalette(
            background = Color(0xFF110C24),
            ink = Color(0xFFF0E9FF),
            muted = Color(0xFF9787B5),
            accent = Color(0xFFC29BFF)
        ),
        light = IknaPalette(
            background = Color(0xFFD1CDD9),
            ink = Color(0xFF1C1230),
            muted = Color(0xFF564867),
            accent = Color(0xFF5D2BA8)
        )
    ),
    // Лагуна. A green-blue field with no brass or coral: calm, cool and distinct
    // from both the reading-room green and the harder cyan of Иней.
    IknaPaletteSpec(
        id = "lagoon",
        nameKey = "set.142",
        dark = IknaPalette(
            background = Color(0xFF071918),
            ink = Color(0xFFE2F5F1),
            muted = Color(0xFF7F9F99),
            accent = Color(0xFF69E0C0)
        ),
        light = IknaPalette(
            background = Color(0xFFCCD6D3),
            ink = Color(0xFF10231F),
            muted = Color(0xFF405651),
            accent = Color(0xFF075C4C)
        )
    ),
    // Кобальт. A deep blue field with a yellow signal. Чернила is navy and
    // coral; this keeps the background family but reverses the temperature.
    IknaPaletteSpec(
        id = "cobalt",
        nameKey = "set.143",
        dark = IknaPalette(
            background = Color(0xFF0A132B),
            ink = Color(0xFFEAF0FF),
            muted = Color(0xFF8491B5),
            accent = Color(0xFFFFD45A)
        ),
        light = IknaPalette(
            background = Color(0xFFCDD2DD),
            ink = Color(0xFF121A34),
            muted = Color(0xFF475067),
            accent = Color(0xFF5F4B00)
        )
    )
)

/** The palette behind a stored id. Anything unknown resolves to the default. */
fun paletteSpec(id: String): IknaPaletteSpec =
    IknaPalettes.firstOrNull { it.id == id }
        ?: IknaPalettes.first { it.id == DEFAULT_PALETTE_ID }

/** The app's own palette. */
val DefaultPaletteSpec: IknaPaletteSpec get() = paletteSpec(DEFAULT_PALETTE_ID)

val DarkPalette: IknaPalette get() = DefaultPaletteSpec.dark

val LightPalette: IknaPalette get() = DefaultPaletteSpec.light

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

/**
 * The palette to paint with. Used by the theme and by the system bars.
 *
 * Two independent choices meet here: which palette (the app's identity, the
 * user's to pick) and how it is lit (dark, light, or whatever the phone is doing
 * right now). Only the second one is allowed to change by itself.
 *
 * systemDark defaults to true so that a caller with no window — a preview, a
 * test — gets the dark lighting rather than a compile error.
 */
fun paletteFor(settings: IknaSettings, systemDark: Boolean = true): IknaPalette {
    val spec = paletteSpec(settings.paletteId)
    return when (settings.theme) {
        ThemeMode.DARK -> spec.dark
        ThemeMode.LIGHT -> spec.light
        ThemeMode.SYSTEM -> spec.palette(light = !systemDark)
        ThemeMode.CUSTOM -> customPaletteOf(settings)
    }
}

/**
 * Every Material slot the app actually reads, filled from four colours.
 *
 * onSurfaceVariant is the muted colour and outline is the line colour, so a
 * screen never has to import a constant to draw secondary text — which is what
 * made the old palette impossible to theme.
 */
private fun schemeOf(p: IknaPalette, controls: IknaControlColors) = if (p.light) {
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.background,
        primaryContainer = controls.fill,
        onPrimaryContainer = controls.label,
        secondary = p.accent,
        onSecondary = p.background,
        secondaryContainer = controls.fill,
        onSecondaryContainer = controls.label,
        background = p.background,
        onBackground = p.ink,
        surface = p.panel,
        onSurface = p.ink,
        surfaceVariant = p.panel,
        onSurfaceVariant = p.muted,
        outline = p.line,
        outlineVariant = p.line,
        error = dangerFor(p),
        onError = p.background
    )
} else {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.background,
        primaryContainer = controls.fill,
        onPrimaryContainer = controls.label,
        secondary = p.accent,
        onSecondary = p.background,
        secondaryContainer = controls.fill,
        onSecondaryContainer = controls.label,
        background = p.background,
        onBackground = p.ink,
        surface = p.panel,
        onSurface = p.ink,
        surfaceVariant = p.panel,
        onSurfaceVariant = p.muted,
        outline = p.line,
        outlineVariant = p.line,
        error = dangerFor(p),
        onError = p.background
    )
}

/**
 * Geologica is Ikna's default voice: geometric enough to belong beside the
 * square chrome, but less anonymous than the platform sans. The middle weights
 * are deliberate; body text must not look hairline-thin and headings do not need
 * a 700/800 jump when their size already creates hierarchy.
 *
 * IBM Plex Mono is reserved for service labels and data. It gives counters,
 * percentages and times stable tabular rhythm without turning prose into a
 * developer console.
 */
private val Geologica by lazy { iknaGeologicaFontFamily() }
private val PlexMonoMedium by lazy { iknaPlexMonoMediumFontFamily() }
private val PlexMonoSemiBold by lazy { iknaPlexMonoSemiBoldFontFamily() }

private val WeightBody = FontWeight.Medium
private val WeightBodySmall = FontWeight.Normal
private val WeightTitle = FontWeight.Medium
private val WeightHeadline = FontWeight.SemiBold
private val WeightDisplay = FontWeight.SemiBold

private val IknaTypography by lazy {
    Typography(
        displayLarge = TextStyle(
            fontFamily = Geologica,
            fontSize = 46.sp,
            lineHeight = 52.sp,
            fontWeight = WeightDisplay,
            letterSpacing = (-1.0).sp
        ),
        displayMedium = TextStyle(
            fontFamily = Geologica,
            fontSize = 38.sp,
            lineHeight = 44.sp,
            fontWeight = WeightDisplay,
            letterSpacing = (-0.7).sp
        ),
        displaySmall = TextStyle(
            fontFamily = Geologica,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = WeightDisplay,
            letterSpacing = (-0.4).sp
        ),
        headlineLarge = TextStyle(fontFamily = Geologica, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = WeightHeadline),
        headlineMedium = TextStyle(fontFamily = Geologica, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = WeightHeadline),
        headlineSmall = TextStyle(fontFamily = Geologica, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = WeightHeadline),
        titleLarge = TextStyle(fontFamily = Geologica, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = WeightTitle),
        titleMedium = TextStyle(fontFamily = Geologica, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = WeightTitle),
        titleSmall = TextStyle(fontFamily = Geologica, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = WeightTitle),
        bodyLarge = TextStyle(fontFamily = Geologica, fontSize = 17.sp, lineHeight = 25.sp, fontWeight = WeightBody),
        bodyMedium = TextStyle(fontFamily = Geologica, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = WeightBody),
        bodySmall = TextStyle(fontFamily = Geologica, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = WeightBodySmall),
        labelLarge = TextStyle(
            fontFamily = PlexMonoMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = PlexMonoMedium,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp
        ),
        labelSmall = TextStyle(
            fontFamily = PlexMonoMedium,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.6.sp
        )
    )
}

/** Stable data figures: medium for inline counts, semibold only for large focal values. */
fun iknaNumberStyle(base: TextStyle, strong: Boolean = false): TextStyle = base.copy(
    fontFamily = if (strong) PlexMonoSemiBold else PlexMonoMedium,
    fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
    letterSpacing = 0.sp
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

/**
 * The selected main face changes prose/content roles only. Labels and data stay
 * in Plex Mono so switching to the system or a custom font does not destroy the
 * numeric rhythm that belongs to Ikna itself.
 */
private fun typographyOf(content: FontFamily?): Typography {
    val main = content ?: Geologica
    val base = IknaTypography
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = main),
        displayMedium = base.displayMedium.copy(fontFamily = main),
        displaySmall = base.displaySmall.copy(fontFamily = main),
        headlineLarge = base.headlineLarge.copy(fontFamily = main),
        headlineMedium = base.headlineMedium.copy(fontFamily = main),
        headlineSmall = base.headlineSmall.copy(fontFamily = main),
        titleLarge = base.titleLarge.copy(fontFamily = main),
        titleMedium = base.titleMedium.copy(fontFamily = main),
        titleSmall = base.titleSmall.copy(fontFamily = main),
        bodyLarge = base.bodyLarge.copy(fontFamily = main),
        bodyMedium = base.bodyMedium.copy(fontFamily = main),
        bodySmall = base.bodySmall.copy(fontFamily = main)
    )
}

/** Resolve the user's main-face choice, with Geologica as the safe fallback. */
@Composable
fun rememberContentFont(mode: FontMode, fontName: String): FontFamily {
    return remember(mode, fontName) {
        when (mode) {
            FontMode.GEOLOGICA -> Geologica
            FontMode.CUSTOM -> {
                val file = FontStore.file()
                if (!file.exists() || file.length() <= 0) Geologica
                else runCatching { iknaFontFamily(file) }.getOrDefault(Geologica)
            }
        }
    }
}

@Composable
fun IknaTheme(
    palette: IknaPalette = DarkPalette,
    contentFont: FontFamily? = null,
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val controls = remember(palette) { controlColors(palette) }
    val scheme = remember(palette, controls) { schemeOf(palette, controls) }
    val signalFrameCoordinator = rememberSignalFrameCoordinator(motionEnabled)
    MaterialTheme(
        colorScheme = scheme,
        typography = typographyOf(contentFont),
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
        CompositionLocalProvider(
            LocalContentColor provides palette.ink,
            LocalIknaControlColors provides controls,
            LocalIknaMotionEnabled provides motionEnabled,
            LocalSignalFrameCoordinator provides signalFrameCoordinator
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.background)
            ) {
                content()
                // OUTER Signal Frames live above the screen tree so scroll,
                // transition and tight-row clipping cannot cut off one side.
                // INNER frames still draw locally inside their own hit target.
                SignalFrameOverlay(signalFrameCoordinator)
            }
        }
    }
}
