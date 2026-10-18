package dev.ikna.data.repo

import androidx.room.useWriterConnection
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.export.ProfileTransferCodec
import dev.ikna.data.export.ProfileTransferLimitException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext

/** Runs inside the snapshot's transaction, before loading any transferred entity.
 * Counts stop at MAX_ROWS + 1. Byte queries return only scalar lengths, never text.
 * Raw UTF-8 is a LOWER bound: JSON escaping/keys and legacy identity expansion are
 * checked by the unchanged exact codec after the now-bounded materialization.
 * An upper-bound estimate must not reject a previously accepted profile. */
internal suspend fun IknaDatabase.preflightProfileTransfer(afterLocalId: Long, caller: CoroutineContext, includeReviews: Boolean = true) {
    caller.ensureActive()
    val tables = PROFILE_TRANSFER_TEXT_COLUMNS.filterKeys { includeReviews || it != "reviews" }
    useWriterConnection { connection ->
        for ((table, _) in tables) {
            caller.ensureActive()
            val filter = if (table == "reviews") " WHERE id > ?" else ""
            connection.usePrepared("SELECT COUNT(*) FROM (SELECT 1 FROM $table$filter LIMIT ${ProfileTransferCodec.MAX_ROWS + 1})") { statement ->
                if (table == "reviews") statement.bindLong(1, afterLocalId)
                check(statement.step()) { "Missing profile row count" }
                if (statement.getLong(0) > ProfileTransferCodec.MAX_ROWS) {
                    throw ProfileTransferLimitException(ProfileTransferLimitException.Limit.ROWS)
                }
            }
            caller.ensureActive()
        }
        var bytes = 0L
        for ((table, columns) in tables) {
            caller.ensureActive()
            val lengths = columns.joinToString(" + ") { "COALESCE(length(CAST($it AS BLOB)), 0)" }
            val filter = if (table == "reviews") " WHERE id > ?" else ""
            connection.usePrepared("SELECT $lengths FROM $table$filter") { statement ->
                if (table == "reviews") statement.bindLong(1, afterLocalId)
                while (statement.step()) {
                    caller.ensureActive()
                    val rowBytes = statement.getLong(0)
                    if (rowBytes > ProfileTransferCodec.MAX_BYTES - bytes) {
                        throw ProfileTransferLimitException(ProfileTransferLimitException.Limit.BYTES)
                    }
                    bytes += rowBytes
                }
            }
            caller.ensureActive()
        }
    }
}

// Only strings retained by ProfileTransferCodec/JournalIdentity normalization.
// Numeric row IDs and local undo pointers are deliberately absent. SQL length of
// BLOB counts UTF-8 bytes even for embedded NUL and non-ASCII source material.
private val PROFILE_TRANSFER_TEXT_COLUMNS = linkedMapOf(
    "packs" to listOf("id", "lang", "title"),
    "chunks" to listOf("id", "packId", "lang", "text", "contextSentence", "translation", "audioRef", "ipa", "ipaContext"),
    "chunk_tokens" to listOf("chunkId", "surface", "lemma", "pos"),
    "pack_chunks" to listOf("packId", "chunkId", "primaryContextId", "primaryMeaningId"),
    "chunk_contexts" to listOf("packId", "chunkId", "contextId", "meaningId", "sourceFamily", "contextSentence", "translation", "ipaContext", "tokensJson"),
    "reviews" to listOf("chunkId", "timingDiscardReason", "gradingReason", "inputMethod", "peekSemantics", "fsrsParameters", "eventId", "undoEventId", "purpose")
)
