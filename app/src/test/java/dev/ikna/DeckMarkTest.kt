package dev.ikna

import dev.ikna.ui.decks.DECK_MARK_FALLBACK
import dev.ikna.ui.decks.DECK_SEAL_SIDE
import dev.ikna.ui.decks.deckSealCells
import dev.ikna.ui.decks.deckSealHighlights
import dev.ikna.ui.decks.isDeckSealLetterZone
import dev.ikna.ui.decks.monogramOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckMarkTest {
    @Test fun `language letters stay semantic`() {
        assertEquals("PL", monogramOf("pl", "Polski · core"))
        assertEquals("EN", monogramOf("EN", "English core"))
    }

    @Test fun `imported packs use title initials`() {
        assertEquals("MW", monogramOf("custom", "my words"))
        assertEquals("PO", monogramOf("custom", "Polski"))
        assertEquals("AB", monogramOf("custom", "alpha-beta"))
    }

    @Test fun `unknown and empty marks remain bounded`() {
        assertEquals("HU", monogramOf("hu", "Magyar"))
        assertEquals(DECK_MARK_FALLBACK, monogramOf("custom", "   "))
        listOf("my words", "Polski", "a", "слова из фильмов", "2024 list").forEach { title ->
            assertTrue(monogramOf("custom", title).length in 1..2)
        }
    }

    @Test fun `one installation seed is perfectly stable`() {
        val first = deckSealCells("catalog-en-ru-beginner", 1_700_000_000_123L)
        assertEquals(first, deckSealCells("catalog-en-ru-beginner", 1_700_000_000_123L))
        assertEquals(deckSealHighlights("catalog-en-ru-beginner", 1_700_000_000_123L),
            deckSealHighlights("catalog-en-ru-beginner", 1_700_000_000_123L))
    }

    @Test fun `another device installation gets another pattern`() {
        val id = "catalog-en-ru-beginner"
        assertNotEquals(deckSealCells(id, 1_700_000_000_123L),
            deckSealCells(id, 1_700_000_100_987L))
        assertNotEquals(deckSealHighlights(id, 1_700_000_000_123L),
            deckSealHighlights(id, 1_700_000_100_987L))
    }

    @Test fun `decks installed together still differ by stable id`() {
        val time = 1_700_000_000_123L
        assertNotEquals(deckSealCells("beginner", time), deckSealCells("advanced", time))
    }

    @Test fun `seeded seal stays mirrored and leaves letters clean`() {
        listOf(1L, 17L, 1_700_000_000_123L, Long.MAX_VALUE).forEach { seed ->
            val seal = deckSealCells("deck-$seed", seed)
            assertTrue(seal.size in 12..32)
            assertTrue(seal.none(::isDeckSealLetterZone))
            seal.forEach { index ->
                assertTrue(index in 0 until DECK_SEAL_SIDE * DECK_SEAL_SIDE)
                val row = index / DECK_SEAL_SIDE
                val column = index % DECK_SEAL_SIDE
                val mirror = row * DECK_SEAL_SIDE + DECK_SEAL_SIDE - 1 - column
                assertTrue("$seed/$index", mirror in seal)
            }
            val highlights = deckSealHighlights("deck-$seed", seed)
            assertEquals(4, highlights.size)
            assertTrue(highlights.none(::isDeckSealLetterZone))
        }
    }
}
