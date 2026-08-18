package dev.ikna.data

import dev.ikna.data.pack.MAX_PACK_TITLE
import dev.ikna.data.pack.packTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A deck row shows the name next to a menu, a switch and a progress bar. A
 * catalogue name is written for a list on a wide page, and a name three lines
 * long pushed the bar out of the row and left every row in the list a different
 * height. The cut is made once, on the way into the database, and these are the
 * cases it has to survive.
 */
class PackTitleTest {

    @Test
    fun `a name that fits is not touched at all`() {
        assertEquals("English core chunks", packTitle("English core chunks", "en-ru-core"))
    }

    @Test
    fun `surrounding space is not part of the name`() {
        assertEquals("English core chunks", packTitle("  English core chunks ", "en-ru-core"))
    }

    @Test
    fun `a catalogue name is cut where a word ends`() {
        val cut = packTitle("English from Russian, beginner, Tatoeba sentences", "x")

        assertEquals("English from Russian, beginner, Tatoeba\u2026", cut)
    }

    @Test
    fun `a cut name is never longer than the limit and the mark`() {
        val names = listOf(
            "English from Russian, beginner, Tatoeba sentences",
            "\u0410\u043d\u0433\u043b\u0438\u0439\u0441\u043a\u0438\u0439 \u0438\u0437 \u0440\u0443\u0441\u0441\u043a\u043e\u0433\u043e, \u043d\u0430\u0447\u0430\u043b\u044c\u043d\u044b\u0439, \u043f\u0440\u0435\u0434\u043b\u043e\u0436\u0435\u043d\u0438\u044f Tatoeba",
            "a".repeat(200),
        )

        for (name in names) {
            val cut = packTitle(name, "x")

            assertTrue("too long: $cut", cut.length <= MAX_PACK_TITLE + 1)
            assertTrue("no mark: $cut", cut.endsWith("\u2026"))
        }
    }

    @Test
    fun `one long word is cut inside itself rather than kept whole`() {
        val cut = packTitle("b".repeat(60), "x")

        assertEquals(MAX_PACK_TITLE + 1, cut.length)
        assertEquals("b".repeat(MAX_PACK_TITLE) + "\u2026", cut)
    }

    @Test
    fun `a name with nothing in it falls back to the identifier`() {
        assertEquals("catalog-en-ru-beginner", packTitle(null, "catalog-en-ru-beginner"))
        assertEquals("catalog-en-ru-beginner", packTitle("   ", "catalog-en-ru-beginner"))
    }
}
