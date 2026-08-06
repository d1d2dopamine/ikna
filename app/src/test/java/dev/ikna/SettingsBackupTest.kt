package dev.ikna

import dev.ikna.data.export.SettingsBackup
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.LoadPreset
import dev.ikna.data.prefs.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A restore that gives back every card and none of the look is a restore the
 * user does not believe worked. These are the two things that have to hold: the
 * file survives a round trip, and it is never mistaken for the review log — the
 * two are picked with the same button.
 */
class SettingsBackupTest {

    @Test
    fun `round trips through json`() {
        val settings = IknaSettings(
            theme = ThemeMode.CUSTOM,
            customBackground = 0x11223344,
            customInk = 0x55667788,
            load = LoadPreset.DENSE,
            autoLoad = false,
            reminderHour = 7,
            reminderMinute = 30,
            animations = false,
            speechEnabled = false,
            speechVoices = "pl=oda;ru=dfc",
            fontName = "Atkinson.ttf"
        )

        val decoded = SettingsBackup.decode(SettingsBackup.encode(settings))

        assertEquals(ThemeMode.CUSTOM.name, decoded?.theme)
        assertEquals(0x11223344, decoded?.customBackground)
        assertEquals(LoadPreset.DENSE.name, decoded?.load)
        assertEquals(false, decoded?.autoLoad)
        assertEquals(7, decoded?.reminderHour)
        assertEquals(false, decoded?.animations)
        assertEquals("pl=oda;ru=dfc", decoded?.speechVoices)
        assertEquals("Atkinson.ttf", decoded?.fontName)
    }

    @Test
    fun `a review log line is not a settings file`() {
        val logLine = "{\"id\":1,\"chunkId\":\"pl-0001\",\"level\":0,\"ts\":1700000000000}"
        assertFalse(SettingsBackup.looksLikeSettings(logLine))
        assertNull(SettingsBackup.decode(logLine))
    }

    @Test
    fun `a settings file is recognised before it is parsed`() {
        assertTrue(SettingsBackup.looksLikeSettings(SettingsBackup.encode(IknaSettings())))
    }

    @Test
    fun `garbage decodes to nothing instead of throwing`() {
        assertNull(SettingsBackup.decode(""))
        assertNull(SettingsBackup.decode("not json at all"))
        assertNull(SettingsBackup.decode("{\"kind\":\"something-else\",\"version\":1}"))
    }

    @Test
    fun `an unknown future field does not break an older build`() {
        val text = "{\"kind\":\"ikna-settings\",\"version\":9,\"theme\":\"LIGHT\",\"somethingNew\":42}"
        assertEquals("LIGHT", SettingsBackup.decode(text)?.theme)
    }
}
