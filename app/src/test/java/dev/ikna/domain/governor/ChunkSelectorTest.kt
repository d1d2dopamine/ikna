package dev.ikna.domain.governor

import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the selector chooses has to depend on the chunk, not on which other
 * chunks happened to be in the same batch.
 *
 * The frequency term used to be `1 - rank / maxRank`, where `maxRank` was the
 * largest rank *in the candidate batch*. The batch is whatever the frequency
 * query returned that day, so the same chunk scored differently on different
 * days for reasons that had nothing to do with it: in a batch of common words
 * the spread was stretched across the full range and frequency dominated the
 * choice, and in a batch of rare ones it collapsed to almost nothing. The
 * component layer -- the reason this selector exists -- was being outvoted by an
 * accident of pagination.
 */
class ChunkSelectorTest {

    private val selector = ChunkSelector()
    private val now = 1_770_000_000_000L

    private fun chunk(id: String, freqRank: Int) = ChunkEntity(
        id = id,
        packId = "p1",
        lang = "en",
        text = "word",
        contextSentence = "a sentence with word in it",
        translation = "перевод",
        targetStart = 0,
        targetEnd = 4,
        freqRank = freqRank
    )

    /** One unknown content word: the shape the selector likes best. */
    private fun tokens(id: String) = listOf(
        ChunkTokenEntity(
            chunkId = id,
            position = 0,
            surface = "word",
            lemma = "word-$id",
            pos = "NOUN",
            isTarget = true,
            isContent = true,
            weight = 1.0
        )
    )

    private fun scoreOf(target: String, pool: List<ChunkEntity>): Double {
        val tokensByChunk = pool.associate { it.id to tokens(it.id) }
        val scored = selector.select(pool, tokensByChunk, emptyMap(), now, pool.size)
        return scored.first { it.chunk.id == target }.score
    }

    @Test
    fun `a chunk scores the same whatever it is batched with`() {
        val target = chunk("target", freqRank = 100)
        val withRareOne = scoreOf("target", listOf(target, chunk("other", freqRank = 9000)))
        val withCommonOne = scoreOf("target", listOf(target, chunk("other", freqRank = 200)))
        val alone = scoreOf("target", listOf(target))

        assertEquals(withRareOne, withCommonOne, 1e-9)
        assertEquals(withRareOne, alone, 1e-9)
    }

    @Test
    fun `common phrases still come first`() {
        val pool = listOf(chunk("rare", freqRank = 9000), chunk("common", freqRank = 10))
        val tokensByChunk = pool.associate { it.id to tokens(it.id) }
        val scored = selector.select(pool, tokensByChunk, emptyMap(), now, 2)
        assertEquals("common", scored.first().chunk.id)
        assertTrue(scored[0].score > scored[1].score)
    }

    @Test
    fun `frequency never outweighs the shape of the chunk`() {
        // A very common chunk with four unknown words against a rare one with a
        // single unknown word: the rare one is still the better lesson.
        val crowded = chunk("crowded", freqRank = 1)
        val clean = chunk("clean", freqRank = 9000)
        val tokensByChunk = mapOf(
            "crowded" to (0..3).map {
                ChunkTokenEntity("crowded", it, "w$it", "lemma$it", "NOUN", false, true, 1.0)
            },
            "clean" to tokens("clean")
        )
        val scored = selector.select(listOf(crowded, clean), tokensByChunk, emptyMap(), now, 2)
        assertEquals(
            "i+1 is the whole idea: one unknown word at a time.",
            "clean",
            scored.first().chunk.id
        )
    }

    @Test
    fun `a chunk with no content words is skipped rather than guessed at`() {
        val empty = chunk("empty", freqRank = 5)
        val scored = selector.select(
            listOf(empty),
            mapOf("empty" to listOf(ChunkTokenEntity("empty", 0, "the", "the", "DET", false, false, 0.1))),
            emptyMap(),
            now,
            1
        )
        assertTrue(scored.isEmpty())
    }
}
