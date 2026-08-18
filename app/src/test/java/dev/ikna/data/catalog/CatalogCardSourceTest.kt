package dev.ikna.data.catalog

import dev.ikna.data.db.ChunkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCardSourceTest {
    private fun chunk(packId: String = "catalog-en-ru-beginner") = ChunkEntity(
        id = "en-ru-beginner-0001",
        packId = packId,
        lang = "en",
        text = "example",
        contextSentence = "This is an example.",
        translation = "Это пример.\n— Tatoeba #12345",
        targetStart = 11,
        targetEnd = 18,
        freqRank = 1
    )

    @Test
    fun `a catalogue meaning is separated from its source`() {
        val parsed = catalogMeaning("Это пример.\n— Tatoeba #12345")
        assertEquals("Это пример.", parsed.text)
        assertEquals("12345", parsed.tatoebaId)
    }

    @Test
    fun `ordinary dashes and malformed identifiers remain ordinary text`() {
        val ordinary = "Не источник — Tatoeba #word"
        assertEquals(CatalogMeaning(ordinary), catalogMeaning(ordinary))
        assertEquals(CatalogMeaning("line\n— Tatoeba #0"), catalogMeaning("line\n— Tatoeba #0"))
        assertNull(tatoebaSentenceUrl("../../7"))
        assertNull(tatoebaSentenceUrl("0"))
    }

    @Test
    fun `a source address always belongs to Tatoeba`() {
        assertEquals(
            "https://tatoeba.org/en/sentences/show/12345",
            tatoebaSentenceUrl("12345")
        )
    }

    @Test
    fun `a report contains public card data and no review state`() {
        val report = catalogCardReport(chunk())!!
        assertTrue(report.contains("Deck: catalog-en-ru-beginner"))
        assertTrue(report.contains("Chunk: en-ru-beginner-0001"))
        assertTrue(report.contains("Source: https://tatoeba.org/en/sentences/show/12345"))
        assertTrue(report.contains("Translation: Это пример."))
        assertTrue(report.endsWith("Problem: "))
        assertFalse(report.contains("stability", ignoreCase = true))
    }

    @Test
    fun `a user deck cannot masquerade as a catalogue report`() {
        assertNull(catalogCardReport(chunk(packId = "user-import")))
    }
}
