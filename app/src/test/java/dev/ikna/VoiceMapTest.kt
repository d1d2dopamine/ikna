package dev.ikna

import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.encodeVoiceMap
import dev.ikna.data.prefs.parseVoiceMap
import dev.ikna.data.prefs.voiceFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The voice map is stored as one string, and it is read on every card. A parse
 * failure here would not be a wrong voice, it would be a crash in the middle of
 * a session, so malformed input has to come back as "no choice" every time.
 */
class VoiceMapTest {

    @Test
    fun `round trips`() {
        val map = mapOf("pl" to "pl-pl-x-oda-local", "ru" to "ru-ru-x-dfc-local")
        assertEquals(map, parseVoiceMap(encodeVoiceMap(map)))
    }

    @Test
    fun `empty string is an empty map`() {
        assertEquals(emptyMap<String, String>(), parseVoiceMap(""))
        assertEquals("", encodeVoiceMap(emptyMap()))
    }

    @Test
    fun `malformed pairs are skipped, not thrown`() {
        val parsed = parseVoiceMap(";;pl=;=voice;ru=ru-ru-x-dfc-local;garbage")
        assertEquals(mapOf("ru" to "ru-ru-x-dfc-local"), parsed)
    }

    @Test
    fun `a voice name containing an equals sign survives`() {
        val map = mapOf("pl" to "weird=name")
        assertEquals(map, parseVoiceMap(encodeVoiceMap(map)))
    }

    @Test
    fun `region tags match the deck language`() {
        val settings = IknaSettings(speechVoices = "pl-PL=oda;ru=dfc")
        assertEquals("oda", settings.voiceFor("pl"))
        assertEquals("dfc", settings.voiceFor("ru-RU"))
    }

    @Test
    fun `an unknown language has no voice`() {
        val settings = IknaSettings(speechVoices = "pl=oda")
        assertNull(settings.voiceFor("en"))
        assertNull(settings.voiceFor(""))
        assertNull(IknaSettings().voiceFor("pl"))
    }
}
