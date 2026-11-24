package dev.ikna.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PalettePreviewTest {
    @Test
    fun backdropKeepsTheEmblemLegibleOnPureBlack() {
        val (ink, accent) = brandPatternCellColors(Color.Black, Color.White, Color.White, 0.65f)
        assertTrue(ink.red in 0.09f..0.11f)
        assertTrue(accent.red in 0.16f..0.18f)
        assertTrue(accent.red > ink.red)
    }

    @Test
    fun backdropKeepsItsUsualMixAwayFromBlack() {
        val palette = DefaultPaletteSpec.grey
        val intensity = 0.65f
        val (ink, accent) = brandPatternCellColors(palette.background, palette.ink, palette.accent, intensity)
        assertEquals(lerp(palette.background, palette.ink, 0.085f * intensity), ink)
        assertEquals(lerp(palette.background, palette.accent, 0.20f * intensity), accent)
    }

    @Test
    fun everyAuthoredPreviewMatchesItsSelectedLightingDespiteDarkLuminance() {
        for (spec in IknaPalettes) {
            for (systemDark in listOf(false, true)) {
                for (mode in listOf(ThemeMode.DARK, ThemeMode.GREY, ThemeMode.SYSTEM)) {
                    val settings = IknaSettings(paletteId = spec.id, theme = mode)
                    val preview = spec.palette(grey = palettePreviewUsesGrey(settings, systemDark))
                    assertEquals(paletteFor(settings, systemDark), preview, "${spec.id} $mode $systemDark")
                }
            }
            assertFalse(spec.grey.light, "Grey is still a dark background: ${spec.id}")
            assertTrue(palettePreviewUsesGrey(IknaSettings(theme = ThemeMode.GREY, paletteId = spec.id)))
        }
    }

    @Test
    fun customPreviewRetainsItsBrightnessFallback() {
        val custom = IknaSettings(theme = ThemeMode.CUSTOM)
        assertFalse(palettePreviewUsesGrey(custom.copy(customBackground = 0xFF101010.toInt())))
        assertTrue(palettePreviewUsesGrey(custom.copy(customBackground = 0xFFF0F0F0.toInt())))
    }
}
