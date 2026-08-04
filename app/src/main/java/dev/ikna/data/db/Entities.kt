package dev.ikna.data.db

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
    val installedAt: Long
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
)

// APPEND ONLY. Source of truth for everything else in the database.
// Migrations may add columns. Migrations may never rewrite or delete rows.
@Entity(tableName = "reviews", indices = [Index("ts"), Index("chunkId")])
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
    val wasAmnesty: Boolean
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
    val accuracy: Double,
    val planCompleted: Boolean
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
