package dev.ikna.domain.phonetics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The gate in front of the renderer.
 *
 * Three different situations mean "draw no pronunciation line", and the whole
 * value of this object is that the screen does not have to know they are
 * different: a deck switched off, a deck with no transcription in it, and a
 * language nothing can transcribe all come back as null. Most of this file is
 * about proving that.
 */
class PhoneticsTest {

    @Before
    fun clear() {
        Phonetics.clearCache()
    }

    // ---- reading the stored value ------------------------------------------

    @Test
    fun `each mode survives a round trip through its stored name`() {
        for (mode in PhoneticsMode.entries) {
            assertEquals(mode, PhoneticsMode.of(mode.stored))
        }
    }

    @Test
    fun `the stored names are the ones that were promised`() {
        // Written out rather than derived from the constant names, because the
        // point of the test is that renaming a constant must not silently change
        // what an existing preference means.
        assertEquals("off", PhoneticsMode.OFF.stored)
        assertEquals("respell", PhoneticsMode.RESPELL.stored)
        assertEquals("ipa", PhoneticsMode.IPA.stored)
    }

    @Test
    fun `anything unrecognised falls back to the default`() {
        // This parses a string that may have come from an older build, or from
        // a backup somebody edited by hand. None of those is a reason to refuse
        // to open a deck.
        assertEquals(PhoneticsMode.DEFAULT, PhoneticsMode.of(null))
        assertEquals(PhoneticsMode.DEFAULT, PhoneticsMode.of(""))
        assertEquals(PhoneticsMode.DEFAULT, PhoneticsMode.of("   "))
        assertEquals(PhoneticsMode.DEFAULT, PhoneticsMode.of("phonetic"))
        assertEquals(PhoneticsMode.DEFAULT, PhoneticsMode.of("OFF!"))
    }

    @Test
    fun `case and padding do not matter`() {
        assertEquals(PhoneticsMode.IPA, PhoneticsMode.of("IPA"))
        assertEquals(PhoneticsMode.IPA, PhoneticsMode.of("  Ipa "))
        assertEquals(PhoneticsMode.OFF, PhoneticsMode.of("Off"))
    }

    @Test
    fun `the default is on`() {
        // Recorded as a test because it is a decision rather than an accident,
        // and because it differs from the app's other pronunciation feature:
        // speech is off by default, this is not. A line of text cannot make a
        // noise in a quiet room.
        assertEquals(PhoneticsMode.RESPELL, PhoneticsMode.DEFAULT)
    }

    // ---- the three ways of having nothing to draw --------------------------

    @Test
    fun `a deck switched off draws nothing`() {
        assertNull(
            Phonetics.line("\u02C8da\u014Bk\u0259", "de", PhoneticsMode.OFF)
        )
    }

    @Test
    fun `a deck with no transcription draws nothing`() {
        // Every deck published before this release is in this state, and so is
        // every deck anybody wrote by hand.
        assertNull(Phonetics.line(null, "de", PhoneticsMode.RESPELL))
        assertNull(Phonetics.line("", "de", PhoneticsMode.RESPELL))
        assertNull(Phonetics.line("   ", "de", PhoneticsMode.RESPELL))
        assertNull(Phonetics.line(null, "de", PhoneticsMode.IPA))
    }

    @Test
    fun `a language nothing can transcribe draws nothing`() {
        // Chinese and Japanese are meaning languages in this app rather than
        // learnable ones, so this should never come up -- but a deck whose
        // language was changed by hand in the deck settings can reach it.
        assertNull(Phonetics.line("\u02C8ni\u02D0", "zh", PhoneticsMode.RESPELL))
        assertNull(Phonetics.line("\u02C8ni\u02D0", "ja", PhoneticsMode.IPA))
        assertNull(Phonetics.line("\u02C8ni\u02D0", "", PhoneticsMode.RESPELL))
    }

    @Test
    fun `all eight learnable languages are supported`() {
        val expected = setOf("en", "ru", "pl", "es", "fr", "de", "it", "pt")
        assertEquals(expected, Phonetics.SUPPORTED)
    }

    // ---- the two ways of having something to draw --------------------------

    @Test
    fun `the ipa mode shows the stored string untouched`() {
        val ipa = "d\u0361\u0291\u025B\u014B\u02C8kuj\u025B"
        assertEquals(ipa, Phonetics.line(ipa, "pl", PhoneticsMode.IPA))
    }

    @Test
    fun `the ipa mode trims but does not rewrite`() {
        val ipa = "\u02C8da\u014Bk\u0259"
        assertEquals(ipa, Phonetics.line("  " + ipa + "  ", "de", PhoneticsMode.IPA))
    }

    @Test
    fun `the respell mode goes through the renderer`() {
        assertEquals(
            "jeng-KOO-yeh",
            Phonetics.line("d\u0361\u0291\u025B\u014B\u02C8kuj\u025B", "pl", PhoneticsMode.RESPELL)
        )
    }

    @Test
    fun `input the renderer cannot use draws nothing rather than an empty line`() {
        // Null and not "" -- the caller uses this to decide whether the line
        // exists at all, and an empty string that still reserves height leaves a
        // gap under the phrase that reads as a layout fault.
        assertNull(Phonetics.line("\u2603\u2604", "pl", PhoneticsMode.RESPELL))
    }

    // ---- the cache ---------------------------------------------------------

    @Test
    fun `the cache does not change the answer`() {
        val ipa = "sp\u0250\u02C8s\u02B2ib\u0259"
        val first = Phonetics.respell(ipa)
        val second = Phonetics.respell(ipa)
        assertEquals(first, second)
        assertEquals("spuh-SEE-buh", first)
    }

    @Test
    fun `clearing the cache does not change the answer either`() {
        val ipa = "\u02C8da\u014Bk\u0259"
        val before = Phonetics.respell(ipa)
        Phonetics.clearCache()
        assertEquals(before, Phonetics.respell(ipa))
    }

    @Test
    fun `the cache survives being overrun`() {
        // Well past the limit, so the map is emptied and refilled at least
        // twice. The only thing that may not happen is a wrong answer.
        for (i in 0 until Phonetics.CACHE_LIMIT * 3) {
            Phonetics.respell("b\u0254ku" + i)
        }
        assertEquals("spuh-SEE-buh", Phonetics.respell("sp\u0250\u02C8s\u02B2ib\u0259"))
    }

    // ---- the preview on the deck settings screen ---------------------------

    @Test
    fun `every supported language has a sample`() {
        // The settings section is only shown for a supported language, and it
        // always draws a preview line, so a missing sample would be a visible
        // hole rather than a quiet omission.
        for (lang in Phonetics.SUPPORTED) {
            assertTrue(
                "no sample for " + lang,
                Phonetics.SAMPLES.containsKey(lang)
            )
        }
    }

    @Test
    fun `a sample renders in both visible modes`() {
        for (lang in Phonetics.SUPPORTED) {
            assertNotNull(
                "no respelled sample for " + lang,
                Phonetics.sample(lang, PhoneticsMode.RESPELL)
            )
            assertNotNull(
                "no ipa sample for " + lang,
                Phonetics.sample(lang, PhoneticsMode.IPA)
            )
        }
    }

    @Test
    fun `a sample shows nothing when the mode shows nothing`() {
        assertNull(Phonetics.sample("pl", PhoneticsMode.OFF))
        assertNull(Phonetics.sample("zh", PhoneticsMode.RESPELL))
    }

    @Test
    fun `a sample names the word it is transcribing`() {
        val shown = Phonetics.sample("pl", PhoneticsMode.RESPELL)
        assertNotNull(shown)
        assertTrue(
            "the sample does not show the word: " + shown,
            shown!!.startsWith("dzi\u0119kuj\u0119")
        )
        assertTrue(
            "the sample does not show the sound: " + shown,
            shown.endsWith("jeng-KOO-yeh")
        )
    }
}
