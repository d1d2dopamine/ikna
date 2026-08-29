package dev.ikna.data.prefs

import dev.ikna.domain.phonetics.PhoneticsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hand-rolled "packId=mode" string, and what it does when it is damaged.
 *
 * This is stored in a preference and it travels in the settings backup, which
 * means it can be edited by hand, truncated by a failed write, or written by a
 * build that knew different mode names. The rule this file exists to hold is
 * that a broken entry costs exactly one deck its setting, and never anything
 * more than that -- the same rule the deck looks beside it follow, tested the
 * same way and for the same reason.
 */
class DeckPhoneticsTest {

    @Test
    fun `a mode survives being written and read back`() {
        val encoded = encodeDeckPhonetics(
            mapOf(
                "catalog-pl-ru-beginner" to PhoneticsMode.RESPELL,
                "catalog-fr-en-middle" to PhoneticsMode.IPA,
                "my-own-deck" to PhoneticsMode.OFF
            )
        )
        val parsed = parseDeckPhonetics(encoded)

        assertEquals(3, parsed.size)
        assertEquals(PhoneticsMode.RESPELL, parsed["catalog-pl-ru-beginner"])
        assertEquals(PhoneticsMode.IPA, parsed["catalog-fr-en-middle"])
        assertEquals(PhoneticsMode.OFF, parsed["my-own-deck"])
    }

    @Test
    fun `nothing stored means nothing parsed`() {
        assertTrue(parseDeckPhonetics("").isEmpty())
        assertTrue(parseDeckPhonetics("   ").isEmpty())
        assertTrue(parseDeckPhonetics(";;;").isEmpty())
    }

    @Test
    fun `a deck nobody decided about gets the default`() {
        val settings = IknaSettings(deckPhonetics = "one=ipa")
        assertEquals(PhoneticsMode.IPA, settings.phoneticsFor("one"))
        assertEquals(PhoneticsMode.DEFAULT, settings.phoneticsFor("two"))
    }

    @Test
    fun `an install that has never opened the setting gets the default everywhere`() {
        val settings = IknaSettings()
        assertEquals("", settings.deckPhonetics)
        assertEquals(PhoneticsMode.DEFAULT, settings.phoneticsFor("anything"))
    }

    // ---- damage ------------------------------------------------------------

    @Test
    fun `a damaged pair loses one deck and no others`() {
        // The entry in the middle has no equals sign at all. The two either side
        // of it must be unaffected: this string is read on the deck screen, and
        // one bad line must not take the pronunciation setting off every deck.
        val parsed = parseDeckPhonetics("one=ipa;garbage;three=off")
        assertEquals(2, parsed.size)
        assertEquals(PhoneticsMode.IPA, parsed["one"])
        assertEquals(PhoneticsMode.OFF, parsed["three"])
    }

    @Test
    fun `an unknown mode is dropped rather than defaulted`() {
        // Dropped, so the deck behaves like a deck with no entry. Defaulting it
        // here would mean a mangled entry quietly switches a deck to something
        // nobody chose, which is harder to notice and harder to explain.
        val parsed = parseDeckPhonetics("one=phonetic;two=respell")
        assertEquals(1, parsed.size)
        assertEquals(PhoneticsMode.RESPELL, parsed["two"])

        val settings = IknaSettings(deckPhonetics = "one=phonetic")
        assertEquals(PhoneticsMode.DEFAULT, settings.phoneticsFor("one"))
    }

    @Test
    fun `an entry with no id or no mode is dropped`() {
        assertTrue(parseDeckPhonetics("=ipa").isEmpty())
        assertTrue(parseDeckPhonetics("one=").isEmpty())
        assertTrue(parseDeckPhonetics("=").isEmpty())
    }

    @Test
    fun `a truncated string keeps whatever came before the cut`() {
        // What a half-finished write leaves behind.
        val parsed = parseDeckPhonetics("one=ipa;two=res")
        assertEquals(1, parsed.size)
        assertEquals(PhoneticsMode.IPA, parsed["one"])
    }

    @Test
    fun `padding around a mode is tolerated`() {
        // Somebody who opens an exported backup and tidies it up.
        val parsed = parseDeckPhonetics("one= ipa ;two=RESPELL")
        assertEquals(PhoneticsMode.IPA, parsed["one"])
        assertEquals(PhoneticsMode.RESPELL, parsed["two"])
    }

    // ---- what is written ----------------------------------------------------

    @Test
    fun `the default is written out rather than left implied`() {
        // Unlike a deck's look, which drops back to nothing when it is plain.
        // Storing only what differs from the default would mean that changing
        // the default in a later release silently changes the behaviour of every
        // deck somebody had already made up their mind about.
        val encoded = encodeDeckPhonetics(mapOf("one" to PhoneticsMode.DEFAULT))
        assertTrue(
            "the default was not written: " + encoded,
            encoded.isNotEmpty()
        )
        assertEquals(PhoneticsMode.DEFAULT, parseDeckPhonetics(encoded)["one"])
    }

    @Test
    fun `an entry with a blank id is never written`() {
        assertEquals("", encodeDeckPhonetics(mapOf("" to PhoneticsMode.IPA)))
        assertEquals("", encodeDeckPhonetics(mapOf("  " to PhoneticsMode.IPA)))
    }

    @Test
    fun `no mode name can break the format`() {
        // The separators this format is built out of. A deck label has to be
        // stripped of these because a person types it; a mode name does not,
        // because the app writes it -- and this test is what keeps that true if
        // somebody adds a fourth mode.
        for (mode in PhoneticsMode.entries) {
            assertTrue(
                "the stored name of " + mode + " contains a separator",
                mode.stored.none { it == ';' || it == '=' || it == '|' }
            )
            assertTrue(
                "the stored name of " + mode + " is blank",
                mode.stored.isNotBlank()
            )
        }
    }

    @Test
    fun `a full round trip through the whole map is stable`() {
        val original = mapOf(
            "a" to PhoneticsMode.OFF,
            "b" to PhoneticsMode.RESPELL,
            "c" to PhoneticsMode.IPA
        )
        val once = encodeDeckPhonetics(original)
        val twice = encodeDeckPhonetics(parseDeckPhonetics(once))
        assertEquals(parseDeckPhonetics(once), parseDeckPhonetics(twice))
    }
}
