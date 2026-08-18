package dev.ikna.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreviewTest {
    private fun line(id: Int, translation: String = "перевод\n— Tatoeba #$id"): String =
        """{"id":"deck-$id","text":"word$id","context":"A word$id here.","translation":"${translation.replace("\n", "\\n")}","targetStart":2,"targetEnd":7,"freqRank":$id,"tokens":[]}"""

    @Test
    fun `preview reads three real cards and separates sources`() {
        val preview = parseCatalogPreview((1..5).joinToString("\n") { line(it) })

        assertEquals(3, preview.size)
        assertEquals("word1", preview.first().text)
        assertEquals("перевод", preview.first().translation)
        assertEquals("1", preview.first().tatoebaId)
    }

    @Test
    fun `bad lines do not consume the preview allowance`() {
        val text = listOf("not json", line(7), "{}", line(8)).joinToString("\n")
        val preview = parseCatalogPreview(text, limit = 3)

        assertEquals(listOf("word7", "word8"), preview.map { it.text })
    }

    @Test
    fun `zero requested cards returns no cards`() {
        assertTrue(parseCatalogPreview(line(1), limit = 0).isEmpty())
    }
}
