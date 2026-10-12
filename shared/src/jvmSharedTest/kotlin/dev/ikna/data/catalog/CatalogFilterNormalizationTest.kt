package dev.ikna.data.catalog

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class CatalogFilterNormalizationTest {
    private fun deck(id: String, lang: String, meaning: String, subject: String, level: String) =
        CatalogDeck(id = id, title = id, lang = lang, meaningLang = meaning,
            subject = subject, level = level)

    private val index = CatalogIndex(decks = listOf(
        deck("en-ru-food", "en", "ru", "food", "beginner"),
        deck("en-ru-travel", "en", "ru", "travel", "advanced"),
        deck("en-pl-travel", "en", "pl", "travel", "middle"),
        deck("fr-de-food", "fr", "de", "food", "beginner")
    ))

    @Test
    fun changingLearningLanguageClearsUnavailableMeaningButKeepsValidDetails() {
        val next = normalizeCatalogFilter(index,
            CatalogFilter(lang = "fr", meaningLang = "ru", subject = "food", level = "beginner"))
        assertEquals(CatalogFilter(lang = "fr", subject = "food", level = "beginner"), next)
        assertEquals(listOf("fr-de-food"), decksFor(index, next).map { it.id })
    }

    @Test
    fun changingMeaningLanguageClearsUnavailableTopicAndLevelInOrder() {
        val next = normalizeCatalogFilter(index,
            CatalogFilter(lang = "en", meaningLang = "pl", subject = "food", level = "beginner"))
        assertEquals(CatalogFilter(lang = "en", meaningLang = "pl"), next)
        assertEquals(listOf("travel"), subjectsFor(index, next))
        assertEquals(listOf("middle"), levelsFor(index, next))
        assertEquals(listOf("en-pl-travel"), decksFor(index, next).map { it.id })
    }

    @Test
    fun changingTopicClearsLevelThatOnlyExistsForAnotherTopic() {
        val requested = CatalogFilter(lang = "en", meaningLang = "ru", subject = "food", level = "advanced")
        assertEquals(listOf("beginner"), levelsFor(index, requested))
        val next = normalizeCatalogFilter(index, requested)
        assertEquals(requested.copy(level = ""), next)
        assertEquals(listOf("en-ru-food"), decksFor(index, next).map { it.id })
    }

    @Test
    fun validSelectionsAndDeliberateSearchArePreserved() {
        val requested = CatalogFilter(lang = "en", meaningLang = "ru", subject = "food", level = "beginner", query = "missing")
        assertEquals(requested, normalizeCatalogFilter(index, requested))
        assertTrue(decksFor(index, requested).isEmpty())
    }

    @Test
    fun replacedOrEmptyIndexClearsUnavailableSelectionsWithoutGuessing() {
        val requested = CatalogFilter(lang = "xx", meaningLang = "yy", subject = "old", level = "expert", query = "kept")
        assertEquals(CatalogFilter(query = "kept"), normalizeCatalogFilter(index, requested))
        assertEquals(CatalogFilter(query = "kept"), normalizeCatalogFilter(CatalogIndex(), requested))
    }

    @Test
    fun normalizationAlwaysLeavesAVisibleChoiceAndMatchingDeckWithoutSearch() {
        for (lang in listOf("", "en", "fr", "missing"))
            for (meaning in listOf("", "ru", "pl", "de", "missing"))
                for (subject in listOf("", "food", "travel", "missing"))
                    for (level in listOf("", "beginner", "middle", "advanced", "missing")) {
                        val next = normalizeCatalogFilter(index, CatalogFilter(lang, meaning, subject, level))
                        assertTrue(next.lang.isEmpty() || next.lang in learnableLangs(index))
                        assertTrue(next.meaningLang.isEmpty() || next.meaningLang in meaningLangsFor(index, next.lang))
                        assertTrue(next.subject.isEmpty() || next.subject in subjectsFor(index, next))
                        assertTrue(next.level.isEmpty() || next.level in levelsFor(index, next))
                        assertTrue(next.toString(), decksFor(index, next).isNotEmpty())
                        assertEquals(next, normalizeCatalogFilter(index, next))
                    }
    }
}
