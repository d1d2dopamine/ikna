package dev.ikna.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue's arithmetic, without a phone and without a network.
 *
 * Everything the screen decides is in CatalogFilter.kt and CatalogFetch.kt's
 * name handling, which is the whole reason those are pure functions: a filter
 * that quietly returns nothing is the failure this catalogue is most likely to
 * have, and it is invisible on a device and obvious here.
 */
class CatalogFilterTest {

    private fun deck(
        id: String,
        lang: String,
        meaningLang: String,
        chunkCount: Int = 100,
        subject: String = "",
        level: String = "",
        licence: String = "CC BY-SA 4.0",
        title: String = id
    ) = CatalogDeck(
        id = id,
        title = title,
        lang = lang,
        meaningLang = meaningLang,
        chunkCount = chunkCount,
        file = "$id.jsonl",
        sizeBytes = 1024L * chunkCount,
        subject = subject,
        level = level,
        licence = licence,
        attribution = "Tatoeba contributors"
    )

    private val index = CatalogIndex(
        builtAt = "2026-08-18",
        decks = listOf(
            deck("en-ru-core", "en", "ru", chunkCount = 4000, level = "beginner"),
            deck("en-ru-more", "en", "ru", chunkCount = 9000, level = "middle"),
            deck("en-ru-food", "en", "ru", chunkCount = 300, subject = "food"),
            deck("pl-ru-core", "pl", "ru", chunkCount = 1200, level = "beginner"),
            deck("fr-de-core", "fr", "de", chunkCount = 200, licence = "CC0 1.0")
        ),
        pairs = listOf(
            CatalogPair("en", "ru", TIER_FULL, deckCount = 3, chunkCount = 13300),
            CatalogPair("pl", "ru", TIER_THIN, deckCount = 1, chunkCount = 1200),
            CatalogPair("fr", "de", TIER_THIN, deckCount = 1, chunkCount = 200)
        )
    )

    @Test
    fun `an empty filter offers everything, biggest first`() {
        val all = decksFor(index, CatalogFilter())
        assertEquals(5, all.size)
        assertEquals("en-ru-more", all.first().id)
        assertEquals("fr-de-core", all.last().id)
    }

    @Test
    fun `a pair narrows the list to that pair`() {
        val decks = decksFor(index, CatalogFilter(lang = "en", meaningLang = "ru"))
        assertEquals(listOf("en-ru-more", "en-ru-core", "en-ru-food"), decks.map { it.id })
    }

    @Test
    fun `a pair that exists only in one direction stays in that direction`() {
        // Learning French from German is in the catalogue; German from French is
        // not, and the screen has to come out empty rather than show the reverse.
        assertTrue(decksFor(index, CatalogFilter(lang = "fr", meaningLang = "de")).isNotEmpty())
        assertTrue(decksFor(index, CatalogFilter(lang = "de", meaningLang = "fr")).isEmpty())
    }

    @Test
    fun `topic and level filter within a pair`() {
        val food = decksFor(index, CatalogFilter(lang = "en", meaningLang = "ru", subject = "food"))
        assertEquals(listOf("en-ru-food"), food.map { it.id })

        val middle = decksFor(index, CatalogFilter(lang = "en", meaningLang = "ru", level = "middle"))
        assertEquals(listOf("en-ru-more"), middle.map { it.id })
    }

    @Test
    fun `search matches the title, the topic and the identifier`() {
        assertEquals(
            listOf("en-ru-food"),
            decksFor(index, CatalogFilter(query = "food")).map { it.id }
        )
        assertEquals(
            listOf("pl-ru-core"),
            decksFor(index, CatalogFilter(query = "PL-RU")).map { it.id }
        )
        assertTrue(decksFor(index, CatalogFilter(query = "klingon")).isEmpty())
    }

    @Test
    fun `the language lists follow the decks that exist`() {
        assertEquals(listOf("en", "fr", "pl"), learnableLangs(index))
        assertEquals(listOf("ru"), meaningLangsFor(index, "en"))
        assertEquals(listOf("de"), meaningLangsFor(index, "fr"))
        // Nothing picked yet: every language the meanings can be in.
        assertEquals(listOf("de", "ru"), meaningLangsFor(index, ""))
    }

    @Test
    fun `topics and levels are only offered where they exist`() {
        val pair = CatalogFilter(lang = "en", meaningLang = "ru")
        assertEquals(listOf("food"), subjectsFor(index, pair))
        // In the order somebody learns, not alphabetically: beginner before middle.
        assertEquals(listOf("beginner", "middle"), levelsFor(index, pair))
        assertEquals(emptyList<String>(), subjectsFor(index, CatalogFilter(lang = "fr")))
        assertEquals(emptyList<String>(), levelsFor(index, CatalogFilter(lang = "fr")))
    }

    @Test
    fun `a pair is full, thin or absent`() {
        assertEquals(TIER_FULL, tierOf(index, "en", "ru"))
        assertEquals(TIER_THIN, tierOf(index, "pl", "ru"))
        assertNull(tierOf(index, "ja", "ru"))
        // Nothing picked is not a claim about any pair.
        assertNull(tierOf(index, "", "ru"))
    }

    @Test
    fun `a pair with decks but no measurement counts as thin`() {
        val older = CatalogIndex(decks = listOf(deck("it-en-core", "it", "en")))
        assertEquals(TIER_THIN, tierOf(older, "it", "en"))
        assertNull(tierOf(older, "it", "ru"))
    }

    @Test
    fun `a deck is stored under a name of its own`() {
        assertEquals("catalog-en-ru-core", catalogPackId("en-ru-core"))
        assertEquals("catalog-en-ru-core", catalogPackId("EN/RU core"))
        assertEquals("catalog-deck", catalogPackId("///"))
        assertEquals("catalog-en-ru-core.jsonl", catalogFileName(deck("en-ru-core", "en", "ru")))
    }

    @Test
    fun `a deck address is built from the release, never from the index`() {
        assertEquals(CATALOG_BASE_URL + "en-ru-core.jsonl", catalogDeckUrl("en-ru-core.jsonl"))
        // Anything trying to leave the release, or to be something other than a
        // deck, is not fetched at all.
        assertNull(catalogDeckUrl("../../etc/passwd"))
        assertNull(catalogDeckUrl("https://example.com/deck.jsonl"))
        assertNull(catalogDeckUrl("deck.apk"))
        assertNull(catalogDeckUrl(""))
    }

    @Test
    fun `the licence questions are answered from the licence itself`() {
        assertTrue(licenceNeedsAttribution("CC BY 4.0"))
        assertTrue(licenceNeedsAttribution("CC BY-SA 4.0"))
        assertFalse(licenceNeedsAttribution("CC0 1.0"))
        assertFalse(licenceNeedsAttribution(""))

        assertTrue(licenceIsShareAlike("CC BY-SA 4.0"))
        assertFalse(licenceIsShareAlike("CC BY 4.0"))
        assertFalse(licenceIsShareAlike("CC0 1.0"))
    }

    @Test
    fun `a missing size says nothing rather than zero`() {
        assertNull(catalogSize(0L))
        assertEquals("0.1", catalogSize(1024L))
        assertEquals("1.0", catalogSize(1024L * 1024L))
    }

    @Test
    fun `progress never reaches a hundred before the file does`() {
        assertEquals(0, progressPercentOf(0L, 100L))
        assertEquals(50, progressPercentOf(50L, 100L))
        assertEquals(100, progressPercentOf(100L, 100L))
        // An unknown length draws nothing rather than a full band.
        assertEquals(0, progressPercentOf(50L, 0L))
        assertEquals(0f, progressFractionOf(50L, 0L), 0.0001f)
        assertEquals(0.5f, progressFractionOf(50L, 100L), 0.0001f)
    }
}
