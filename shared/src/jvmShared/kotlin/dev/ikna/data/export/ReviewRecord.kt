package dev.ikna.data.export

import dev.ikna.data.db.ReviewEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One line of an exported review log.
 *
 * This shape used to exist twice: once as a hand-written `StringBuilder` in the
 * exporter and once as a private class in the restore. Two independent
 * descriptions of the same file format is one too many — a field added on one
 * side and forgotten on the other is silently dropped data, and this file is the
 * only thing in the app that cannot be regenerated.
 *
 * Every field except `chunkId`, `ts` and `rating` has a default, so a file from
 * an older version still reads, and unknown keys are ignored, so a file from a
 * newer one does too.
 */
@Serializable
data class ReviewRecord(
    /**
     * The row id on the phone that wrote the file.
     *
     * Meaningless as an id here — see `RestoreRepository` — but it is exported
     * because `undoOf` points at it, and without it a retraction cannot be
     * matched to the answer it retracts.
     */
    val id: Long = 0,
    val chunkId: String,
    val level: Int = 0,
    val ts: Long,
    val rating: Int,
    val elapsedDays: Double = 0.0,
    val stabilityBefore: Double = 0.0,
    val stabilityAfter: Double = 0.0,
    val difficultyBefore: Double = 5.0,
    val difficultyAfter: Double = 5.0,
    val durationMs: Long = 0L,
    val wasAmnesty: Boolean = false,

    // v2: the undo trail, so a restore replays the history the user actually
    // kept instead of reviving answers they took back.
    val prevStability: Double? = null,
    val prevDifficulty: Double? = null,
    val prevDueAt: Long? = null,
    val prevLastReviewAt: Long? = null,
    val prevReps: Int? = null,
    val prevLapses: Int? = null,
    val prevIsNew: Boolean? = null,
    val prevInAmnesty: Boolean? = null,
    val undoOf: Long? = null
) {
    /** Identity of an answer, independent of which phone stored it. */
    val signature: String get() = chunkId + ":" + level + ":" + ts

    /**
     * @param id the id to store here. Defaults to 0, which means "let SQLite
     *   number it": the id in the file belongs to another database.
     * @param undoOf the retraction target as it is numbered *here*.
     */
    fun toEntity(id: Long = 0L, undoOf: Long? = this.undoOf): ReviewEntity = ReviewEntity(
        id = id,
        chunkId = chunkId,
        level = level,
        ts = ts,
        rating = rating,
        elapsedDays = elapsedDays,
        stabilityBefore = stabilityBefore,
        stabilityAfter = stabilityAfter,
        difficultyBefore = difficultyBefore,
        difficultyAfter = difficultyAfter,
        durationMs = durationMs,
        wasAmnesty = wasAmnesty,
        prevStability = prevStability,
        prevDifficulty = prevDifficulty,
        prevDueAt = prevDueAt,
        prevLastReviewAt = prevLastReviewAt,
        prevReps = prevReps,
        prevLapses = prevLapses,
        prevIsNew = prevIsNew,
        prevInAmnesty = prevInAmnesty,
        undoOf = undoOf
    )

    companion object {
        fun of(r: ReviewEntity): ReviewRecord = ReviewRecord(
            id = r.id,
            chunkId = r.chunkId,
            level = r.level,
            ts = r.ts,
            rating = r.rating,
            elapsedDays = r.elapsedDays,
            stabilityBefore = r.stabilityBefore,
            stabilityAfter = r.stabilityAfter,
            difficultyBefore = r.difficultyBefore,
            difficultyAfter = r.difficultyAfter,
            durationMs = r.durationMs,
            wasAmnesty = r.wasAmnesty,
            prevStability = r.prevStability,
            prevDifficulty = r.prevDifficulty,
            prevDueAt = r.prevDueAt,
            prevLastReviewAt = r.prevLastReviewAt,
            prevReps = r.prevReps,
            prevLapses = r.prevLapses,
            prevIsNew = r.prevIsNew,
            prevInAmnesty = r.prevInAmnesty,
            undoOf = r.undoOf
        )

        /**
         * The one reader and writer of the log format.
         *
         * `explicitNulls = false` keeps the file the size it was: the whole undo
         * trail is absent rather than null on ordinary answers, which is most
         * lines. `ignoreUnknownKeys` lets an older build read a file written by
         * a newer one instead of throwing the line away.
         */
        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}
