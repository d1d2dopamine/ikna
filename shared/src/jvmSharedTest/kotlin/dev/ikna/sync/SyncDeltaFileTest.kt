package dev.ikna.sync

import dev.ikna.data.export.ReviewRecord
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncDeltaFileTest {

    @Test
    fun encodesAndDecodesDeltaEnvelopeWithSha256AndBoundaryLineage() {
        val records = listOf(
            ReviewRecord(
                id = 1L,
                chunkId = "chunk-a",
                level = 1,
                ts = 1_760_000_000_000L,
                rating = 3,
                durationMs = 1800L,
                eventId = "evt-001"
            ),
            ReviewRecord(
                id = 2L,
                chunkId = "chunk-b",
                level = 1,
                ts = 1_760_000_060_000L,
                rating = 1,
                durationMs = 2400L,
                eventId = "evt-002"
            )
        )
        val compressed = SyncDeltaFile.encodeEnvelope(
            records = records,
            profileName = "d1d2",
            historyBoundaryToken = "reset-gen-2",
            nowMillis = 1_760_000_100_000L
        )
        assertTrue(compressed.size > 16)

        val decoded = SyncDeltaFile.decodeEnvelope(compressed)
        assertEquals(2, decoded.recordCount)
        assertEquals("d1d2", decoded.profileName)
        assertEquals("reset-gen-2", decoded.historyBoundaryToken)
        assertEquals(records, decoded.records)

        SyncDeltaFile.validateHistoryLineage(decoded, "reset-gen-2")
        assertFailsWith<IllegalArgumentException> {
            SyncDeltaFile.validateHistoryLineage(decoded, "reset-gen-3")
        }
        assertFailsWith<IllegalArgumentException> {
            SyncDeltaFile.validateHistoryLineage(decoded, null)
        }
    }

    @Test
    fun rejectsTamperedPayloadAndOversizedStreams() {
        val records = listOf(
            ReviewRecord(
                id = 1L,
                chunkId = "chunk-a",
                level = 1,
                ts = 1_760_000_000_000L,
                rating = 3,
                durationMs = 1000L,
                eventId = "evt-tamper"
            )
        )
        val compressed = SyncDeltaFile.encodeEnvelope(records)
        val damaged = compressed.copyOf()
        damaged[damaged.lastIndex] = (damaged[damaged.lastIndex].toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> {
            SyncDeltaFile.decodeEnvelope(damaged)
        }

        assertFailsWith<IllegalArgumentException> {
            SyncDeltaFile.readBounded(ByteArrayInputStream(ByteArray(128)), maxBytes = 64)
        }
    }
}
