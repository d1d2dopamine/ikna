package dev.ikna.data.pack

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkCatalogContractTest {
    @Test
    fun catalogueSpansUseKotlinUtf16AndTokensKeepTheTarget() {
        val root = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val json = Json { ignoreUnknownKeys = true }
        val cards = File(root, "tools/catalog/fixtures/cjk-offsets.jsonl")
            .readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            .map { json.decodeFromString<PackChunk>(it) }
        assertEquals(3, cards.size)
        for (card in cards) {
            assertEquals(card.text, card.context.substring(card.targetStart, card.targetEnd))
            var cursor = 0
            var targetFound = false
            for (token in card.tokens) {
                val start = card.context.indexOf(token.surface, cursor)
                assertTrue("token must occur in source order", start >= cursor)
                val end = start + token.surface.length
                if (start == card.targetStart && end == card.targetEnd) {
                    targetFound = true
                    assertEquals(card.text, token.surface)
                }
                cursor = end
            }
            assertTrue("the target must be a complete token", targetFound)
        }
    }
}
