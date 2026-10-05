package dev.ikna.ui.theme

import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PalettePreviewTest {
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
