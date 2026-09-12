package dev.ikna.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

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

    @Test
    fun `gzip deck bytes decode to the original jsonl`() {
        val text = (1..4).joinToString("\n") { line(it) } + "\n"
        val bytes = ByteArrayOutputStream().also { sink ->
            GZIPOutputStream(sink).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        val deck = CatalogDeck(
            id = "en-ru-everyday-beginner",
            title = "fixture",
            lang = "en",
            meaningLang = "ru",
            file = "en-ru-everyday-beginner.jsonl.gz",
            sizeBytes = bytes.size.toLong(),
            uncompressedSizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong(),
            compression = "gzip"
        )
        assertEquals(text, decodeCatalogDeckBytes(bytes, deck))
    }

    @Test
    fun `broken gzip deck is rejected`() {
        val deck = CatalogDeck(
            id = "broken",
            title = "broken",
            lang = "en",
            meaningLang = "ru",
            file = "broken.jsonl.gz",
            compression = "gzip"
        )
        assertNull(decodeCatalogDeckBytes("not gzip".toByteArray(), deck))
    }
}
