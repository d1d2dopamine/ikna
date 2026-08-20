package dev.ikna.ui.theme

import dev.ikna.data.prefs.DEFAULT_PALETTE_ID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The launch window is not Compose. It is an Android colour resource the system
 * paints before any of this app runs, so nothing in Theme.kt can keep it honest.
 *
 * Three surfaces are read as one: the adaptive launcher icon, the splash field
 * and the first frame of the app. Two of them come from res/values/colors.xml
 * and the third from DEFAULT_PALETTE_ID. Until 0.9.0 they agreed by accident
 * because the default palette had not moved since 0.6.1; when it moved to
 * Чернила the resources stayed behind and every cold launch showed a warm
 * near-black tile in front of a navy app.
 *
 * These tests state the hexes literally, on purpose. A test that read the XML
 * would pass while both sides were wrong together.
 */
class LaunchWindowTest {

    @Test
    fun `the launch window is the default palette's dark background`() {
        assertEquals("0B1120", hexOf(DarkPalette.background))
    }

    @Test
    fun `the widget wears the default palette's dark version`() {
        assertEquals("0B1120", hexOf(DefaultPaletteSpec.dark.background))
        assertEquals("E5EAF4", hexOf(DefaultPaletteSpec.dark.ink))
        assertEquals("78859C", hexOf(DefaultPaletteSpec.dark.muted))
        assertEquals("FF7A5C", hexOf(DefaultPaletteSpec.dark.accent))
    }

    @Test
    fun `the clean install default is still Ink`() {
        assertEquals("ink", DEFAULT_PALETTE_ID)
    }
}
