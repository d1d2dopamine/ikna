package dev.ikna.domain.session

import dev.ikna.data.db.CardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order new chunks arrive in.
 *
 * Introductions are the expensive cards: a first contact costs several times
 * what a repetition costs, and a run of them back to back is where a session
 * stops being possible to finish. The builder spaces them — two new, then three
 * repetitions — so the hard part never arrives in a block.
 *
 * This is easy to break by accident while changing the queue, and impossible to
 * notice by looking at a screenshot, which is why it is tested rather than
 * eyeballed.
 */
class SessionOrderTest {

    // First contact is level 0, never answered, no repetitions yet — the same
    // three conditions the builder checks.
    private fun fresh(id: String) = CardEntity(
        chunkId = id,
        level = 0,
        stability = 0.0,
        difficulty = 5.0,
        dueAt = 0L,
        lastReviewAt = null,
        introducedAt = 0L,
        reps = 0,
        isNew = true
    )

    private fun seen(id: String) = CardEntity(
        chunkId = id,
        level = 1,
        stability = 5.0,
        difficulty = 5.0,
        dueAt = 0L,
        lastReviewAt = 0L,
        introducedAt = 0L,
        reps = 3,
        isNew = false
    )

    private fun shape(cards: List<CardEntity>): String =
        cards.joinToString("") { if (it.isNew) "N" else "R" }

    @Test
    fun `new cards come in pairs separated by repetitions`() {
        val cards = (1..6).map { fresh("n$it") } + (1..9).map { seen("r$it") }

        val out = groupIntroductions(cards, block = 2, gap = 3)

        assertEquals("NNRRRNNRRRNNRRR", shape(out))
    }

    @Test
    fun `no three introductions in a row`() {
        val cards = (1..8).map { fresh("n$it") } + (1..20).map { seen("r$it") }

        val out = groupIntroductions(cards, block = 2, gap = 3)

        var run = 0
        var longest = 0
        for (card in out) {
            run = if (card.isNew) run + 1 else 0
            if (run > longest) longest = run
        }
        assertTrue("introductions bunched up: " + shape(out), longest <= 2)
    }

    @Test
    fun `nothing is lost and nothing is duplicated`() {
        val cards = (1..5).map { fresh("n$it") } + (1..7).map { seen("r$it") }

        val out = groupIntroductions(cards, block = 2, gap = 3)

        assertEquals(cards.size, out.size)
        assertEquals(cards.map { it.key }.toSet(), out.map { it.key }.toSet())
        assertEquals(out.size, out.map { it.key }.toSet().size)
    }

    @Test
    fun `leftover repetitions are kept at the end`() {
        val cards = (1..2).map { fresh("n$it") } + (1..10).map { seen("r$it") }

        val out = groupIntroductions(cards, block = 2, gap = 3)

        assertEquals(12, out.size)
        assertEquals("NNRRRRRRRRRR", shape(out))
    }

    @Test
    fun `too few repetitions to space with is not an error`() {
        val cards = (1..6).map { fresh("n$it") } + (1..2).map { seen("r$it") }

        val out = groupIntroductions(cards, block = 2, gap = 3)

        assertEquals(8, out.size)
        assertEquals(cards.map { it.key }.toSet(), out.map { it.key }.toSet())
    }

    @Test
    fun `a day of only repetitions is left alone`() {
        val cards = (1..6).map { seen("r$it") }

        assertEquals(cards, groupIntroductions(cards, block = 2, gap = 3))
    }

    @Test
    fun `a first run of only new cards is left alone`() {
        val cards = (1..6).map { fresh("n$it") }

        assertEquals(cards, groupIntroductions(cards, block = 2, gap = 3))
    }

    @Test
    fun `nonsense spacing is ignored rather than obeyed`() {
        val cards = (1..3).map { fresh("n$it") } + (1..3).map { seen("r$it") }

        assertEquals(cards, groupIntroductions(cards, block = 0, gap = 3))
        assertEquals(cards, groupIntroductions(cards, block = 2, gap = 0))
    }
}
