package dev.ikna.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The list of cards taken out of rotation by "this card is wrong".
 *
 * It lives in preferences rather than in a table, which is a deliberate trade:
 * the schema is committed to the repository and checked in CI, so a new column
 * is a release of its own, and a correction somebody makes today cannot wait for
 * that. The cost is that the list is one string, so the shape of that string is
 * the whole contract -- hence these tests.
 */
class SuppressedTest {

    @Test
    fun `nothing marked is an empty list`() {
        assertEquals(emptyList<String>(), suppressedOf(""))
        assertEquals(emptyList<String>(), suppressedOf("   "))
        assertEquals(emptyList<String>(), suppressedOf(";;;"))
    }

    @Test
    fun `ids come back in the order they were stored`() {
        // Newest first, because the cap drops the oldest and the most recent
        // correction is the one the person still remembers making.
        assertEquals(
            listOf("neuro-0007", "neuro-0003"),
            suppressedOf("neuro-0007;neuro-0003")
        )
    }

    @Test
    fun `spacing in a hand-edited backup is forgiven`() {
        // This string is exported in the settings backup as plain text, so it can
        // come back with spaces around it after somebody opened the file.
        assertEquals(
            listOf("a-0001", "a-0002"),
            suppressedOf(" a-0001 ; a-0002 ")
        )
    }

    @Test
    fun `the same id cannot appear twice`() {
        assertEquals(listOf("a-0001"), suppressedOf("a-0001;a-0001"))
    }

    @Test
    fun `the cap is far above any real deck and still finite`() {
        // A deck holds up to ten thousand rows. If four hundred of them are wrong
        // the deck is the problem, not the list -- but a preference still must not
        // grow without a bound.
        assertTrue(SUPPRESS_LIMIT in 100..1000)
    }
}
