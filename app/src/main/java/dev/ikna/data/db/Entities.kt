package dev.ikna.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Content (replaceable, comes from packs)
// ---------------------------------------------------------------------------

@Entity(tableName = "chunks", indices = [Index("freqRank"), Index("packId")])
data class ChunkEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val lang: String,
    val text: String,
    val contextSentence: String,
    val translation: String,
    val targetStart: Int,
    val targetEnd: Int,
    val freqRank: Int,
    // Reserved for a later audio pack. Nullable on purpose so adding audio
    // never requires a data migration.
    val audioRef: String? = null
)

/** One local concordance hit, joined to the deck that owns it. */
data class ChunkSearchRow(
    val chunkId: String,
    val packId: String,
    val packTitle: String,
    val lang: String,
    val text: String,
    val contextSentence: String,
    val translation: String,
    val freqRank: Int
)

@Entity(tableName = "chunk_tokens", primaryKeys = ["chunkId", "position"], indices = [Index("lemma", "pos")])
data class ChunkTokenEntity(
    val chunkId: String,
    val position: Int,
    val surface: String,
    val lemma: String,
    val pos: String,
    val isTarget: Boolean,
    val isContent: Boolean,
    val weight: Double
)

@Entity(tableName = "packs")
data class PackEntity(
    @PrimaryKey val id: String,
    val version: Int,
    val lang: String,
    val chunkCount: Int,
    val installedAt: Long,
    // ---- schema v2 -------------------------------------------------------
    val title: String? = null,
    /**
     * Switching a deck off stops NEW chunks being introduced from it. Cards
     * that already exist keep their schedule and their history, so turning a
     * deck off can never read as losing progress.
     */
    @ColumnInfo(defaultValue = "1") val isActive: Boolean = true
)

// ---------------------------------------------------------------------------
// Item layer (scheduled by FSRS)
// ---------------------------------------------------------------------------

// level: 0 = recognition, 1 = cloze, 2 = production
@Entity(
    tableName = "cards",
    primaryKeys = ["chunkId", "level"],
    indices = [Index("dueAt"), Index("inAmnesty")]
)
data class CardEntity(
    val chunkId: String,
    val level: Int,
    val stability: Double,
    val difficulty: Double,
    val dueAt: Long,
    val lastReviewAt: Long?,
    val introducedAt: Long,
    val reps: Int = 0,
    val lapses: Int = 0,
    val inAmnesty: Boolean = false,
    // true until the card has been answered once
    val isNew: Boolean = true
) {
    /** Stable identity of a single question. Used by the daily plan. */
    val key: String get() = chunkId + ":" + level
}

// APPEND ONLY. Source of truth for everything else in the database.
// Migrations may add columns. Migrations may never rewrite or delete rows.
@Entity(tableName = "reviews", indices = [Index("ts"), Index("chunkId"), Index("undoOf")])
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chunkId: String,
    val level: Int,
    val ts: Long,
    val rating: Int,
    val elapsedDays: Double,
    val stabilityBefore: Double,
    val stabilityAfter: Double,
    val difficultyBefore: Double,
    val difficultyAfter: Double,
    val durationMs: Long,
    val wasAmnesty: Boolean,

    // ---- schema v2: undo -------------------------------------------------
    // Snapshot of the card as it was BEFORE this answer. Undo restores it
    // verbatim rather than trying to invert FSRS, which is not invertible.
    val prevStability: Double? = null,
    val prevDifficulty: Double? = null,
    val prevDueAt: Long? = null,
    val prevLastReviewAt: Long? = null,
    val prevReps: Int? = null,
    val prevLapses: Int? = null,
    val prevIsNew: Boolean? = null,
    val prevInAmnesty: Boolean? = null,
    /**
     * When set, this row is not an answer: it retracts review #undoOf.
     * The log stays strictly append-only — nothing is updated, nothing is
     * deleted — and every consumer filters retracted answers out. `rating` is
     * 0 on these rows, which is not a valid FSRS rating, so old readers that
     * are unaware of undo cannot mistake one for a real answer.
     */
    val undoOf: Long? = null
)

// ---------------------------------------------------------------------------
// Derived tables. Fully rebuildable from `reviews`; migrations may drop them.
// ---------------------------------------------------------------------------

@Entity(tableName = "components", primaryKeys = ["lemma", "pos"])
data class ComponentEntity(
    val lemma: String,
    val pos: String,
    val exposures: Double,
    val successes: Double,
    val stabilityEst: Double,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)

@Entity(tableName = "daily_stats")
data class DailyStatEntity(
    @PrimaryKey val day: String,
    val reviewsDone: Int,
    val newIntroduced: Int,
    val activeMs: Long,
    /** Derived from [correctCount]. Kept as a column so older readers still work. */
    val accuracy: Double,
    val planCompleted: Boolean,

    // ---- schema v3 -------------------------------------------------------
    /**
     * Answers rated GOOD or better on this day, as a count.
     *
     * [accuracy] used to be the only record of this, and it was maintained by
     * multiplying the stored average back out by the day's total, adding one and
     * dividing again — with undo running the same arithmetic backwards. Float
     * error accumulated in a number the load governor reads to decide whether
     * the user is allowed new material, and it could drift away from the log it
     * is supposed to summarise. An integer count cannot drift.
     *
     * Added last, with a default, so every positional construction of this row
     * keeps compiling.
     */
    @ColumnInfo(defaultValue = "0") val correctCount: Int = 0
)

@Entity(tableName = "governor_log")
data class GovernorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val day: String,
    val dueToday: Int,
    val forecastAvg3d: Double,
    val backlog: Int,
    val accuracyRecent: Double,
    val daysSinceLastSession: Int,
    val reviewsDoneToday: Int,
    val capacity: Int,
    val headroom: Double,
    val allowedNew: Int,
    val reason: String
)

/**
 * The plan for one calendar day. Added in schema v2 to fix the counter bug.
 *
 * Before this table the plan was recomputed on every entry to the session
 * screen. Answering cards freed governor headroom, the governor then honestly
 * spent that headroom on new chunks, and the number at the top of the screen
 * went UP as the user worked. The day's plan is now decided exactly once and
 * persisted: the set of questions for today can only shrink as they are
 * answered, and it grows only when the user explicitly asks for more.
 */
@Entity(tableName = "daily_plan")
data class DailyPlanEntity(
    @PrimaryKey val day: String,
    /** "chunkId:level" keys in presentation order, comma separated. */
    val plannedIds: String,
    val plannedTotal: Int,
    val capacity: Int,
    val allowedNew: Int,
    val amnestyQuota: Int,
    val reason: String,
    /** How many extra cards the user asked for today, via "ещё немного". */
    val extraRequested: Int,
    val createdAt: Long
) {
    val ids: List<String>
        get() = if (plannedIds.isEmpty()) emptyList() else plannedIds.split(",")
}
