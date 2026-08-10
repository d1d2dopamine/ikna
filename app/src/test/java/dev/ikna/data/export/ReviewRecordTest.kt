package dev.ikna.data.export

import dev.ikna.data.db.ReviewEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The export is the only copy of the review log that survives the app being
 * uninstalled, so the line format is worth pinning: a file that cannot be read
 * back is indistinguishable from no backup at all, and it fails silently, months
 * before anyone tries to use it.
 */
class ReviewRecordTest {

    private fun entity(
        id: Long = 7L,
        chunkId: String = "en-ru-core:12",
        undoOf: Long? = null,
        prevStability: Double? = null
    ) = ReviewEntity(
        id = id,
        chunkId = chunkId,
        level = 1,
        ts = 1_700_000_000_000L,
        rating = 3,
        elapsedDays = 2.5,
        stabilityBefore = 4.0,
        stabilityAfter = 6.5,
        difficultyBefore = 5.0,
        difficultyAfter = 4.8,
        durationMs = 3_200L,
        wasAmnesty = false,
        prevStability = prevStability,
        undoOf = undoOf
    )

    private fun roundTrip(r: ReviewEntity): ReviewRecord {
        val line = ReviewRecord.json.encodeToString(
            ReviewRecord.serializer(),
            ReviewRecord.of(r)
        )
        return ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), line)
    }

    @Test
    fun `a row survives the trip through the file`() {
        val original = ReviewRecord.of(entity())
        assertEquals(original, roundTrip(entity()))
    }

    /**
     * The old exporter pasted `chunkId` between two quote characters. An id
     * containing a quote, a backslash or a newline - which a user-imported pack
     * is free to have - wrote a line that no longer parsed.
     */
    @Test
    fun `an awkward chunk id does not break the line`() {
        val nasty = "pack:\"quoted\"\\slash\nnewline\ttab"
        val line = ReviewRecord.json.encodeToString(
            ReviewRecord.serializer(),
            ReviewRecord.of(entity(chunkId = nasty))
        )
        assertFalse("a record must stay on one line", line.contains('\n'))
        assertEquals(nasty, roundTrip(entity(chunkId = nasty)).chunkId)
    }

    @Test
    fun `the undo trail survives, and is absent when there is none`() {
        val retraction = roundTrip(entity(undoOf = 4L, prevStability = 3.25))
        assertEquals(4L, retraction.undoOf)
        assertEquals(3.25, retraction.prevStability!!, 1e-9)

        // Absent rather than null: the trail is empty on almost every line, and
        // this file is written once a week for years.
        val plain = ReviewRecord.json.encodeToString(
            ReviewRecord.serializer(),
            ReviewRecord.of(entity())
        )
        assertFalse(plain.contains("undoOf"))
        assertFalse(plain.contains("prevStability"))
    }

    /** An older file must still read after a field is added. */
    @Test
    fun `unknown keys are ignored and missing ones default`() {
        val line = """{"chunkId":"a:1","ts":1,"rating":3,"somethingNew":42}"""
        val rec = ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), line)
        assertEquals("a:1", rec.chunkId)
        assertEquals(0, rec.level)
        assertTrue(rec.signature.startsWith("a:1:"))
    }
}
