package dev.ikna.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deck icons and colours live in one preference string, hand-encoded the same
 * way the voice map is. That string is read on every frame of the home screen,
 * it is written by a settings restore, and it can arrive from a file somebody
 * edited by hand -- so the only acceptable failure mode is losing one deck's
 * decoration, never taking the list down.
 */
class DeckLookTest {

    @Test
    fun `a look survives being written and read back`() {
        val map = mapOf(
            "en-ru-core" to DeckLook(emoji = "A", tint = 3),
            "user-kitchen" to DeckLook(emoji = "", tint = 0),
            "user-work" to DeckLook(emoji = "B", tint = NO_TINT)
        )

        val decoded = parseDeckLooks(encodeDeckLooks(map))

        assertEquals(map, decoded)
    }

    @Test
    fun `a deck with nothing chosen is not stored at all`() {
        val encoded = encodeDeckLooks(
            mapOf(
                "user-work" to DeckLook(),
                "user-kitchen" to DeckLook(emoji = "A", tint = NO_TINT)
            )
        )

        assertTrue("plain decks must not be written: $encoded", !encoded.contains("user-work"))
        assertEquals(1, parseDeckLooks(encoded).size)
    }

    @Test
    fun `a damaged pair loses one deck and nothing else`() {
        val decoded = parseDeckLooks("broken;=nothing;user-a=A|2;user-b=")

        assertEquals(mapOf("user-a" to DeckLook(emoji = "A", tint = 2)), decoded)
    }

    @Test
    fun `a colour that is not a number is read as no colour`() {
        assertEquals(
            mapOf("user-a" to DeckLook(emoji = "A", tint = NO_TINT)),
            parseDeckLooks("user-a=A|later")
        )
    }

    @Test
    fun `nothing stored means every deck is plain`() {
        val settings = IknaSettings()

        assertTrue(settings.lookFor("en-ru-core").isPlain)
        assertEquals(DeckLook(), settings.lookFor("anything"))
        assertEquals(emptyMap<String, DeckLook>(), parseDeckLooks(""))
    }

    @Test
    fun `one deck's look is found among several`() {
        val settings = IknaSettings(deckLooks = "user-a=A|1;user-b=B|4")

        assertEquals(DeckLook(emoji = "B", tint = 4), settings.lookFor("user-b"))
        assertTrue(settings.lookFor("user-c").isPlain)
    }

    @Test
    fun `the bar keeps its defaults until somebody changes them`() {
        val settings = IknaSettings()

        assertTrue("the mark is shown out of the box", settings.showWordmark)
        assertTrue("the bar is not mirrored out of the box", !settings.leftHanded)
    }
}
