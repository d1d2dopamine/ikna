package dev.ikna.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change gets an explicit migration here.
 *
 * Rules:
 *  - `reviews` may only gain columns. Never rewrite, never drop.
 *  - Derived tables (`components`, `daily_stats`, `governor_log`) may be dropped
 *    and recreated; they are rebuilt from `reviews` by
 *    [dev.ikna.data.repo.ComponentRepository.rebuildFromReviews].
 *  - Content tables (`chunks`, `chunk_tokens`) may be dropped and recreated;
 *    packs are reinstalled from assets on next launch.
 */
object IknaMigrations {

    /**
     * v1 -> v2
     *
     *  - `reviews` gains the before-state snapshot that makes undo possible,
     *    plus `undoOf` so a retraction is an inserted row rather than an edit.
     *    All new columns are nullable with no default, so rows written by v1
     *    stay valid; an answer from before this update simply cannot be undone.
     *  - `packs` gains a title and an on/off switch for the Decks screen.
     *  - `daily_plan` is new: the day's plan is decided once and persisted,
     *    which is what stops the session counter from growing while the user
     *    answers cards.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevStability REAL")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevDifficulty REAL")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevDueAt INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevLastReviewAt INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevReps INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevLapses INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevIsNew INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN prevInAmnesty INTEGER")
            db.execSQL("ALTER TABLE reviews ADD COLUMN undoOf INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_reviews_undoOf ON reviews (undoOf)")

            db.execSQL("ALTER TABLE packs ADD COLUMN title TEXT")
            db.execSQL("ALTER TABLE packs ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS daily_plan (" +
                    "day TEXT NOT NULL, " +
                    "plannedIds TEXT NOT NULL, " +
                    "plannedTotal INTEGER NOT NULL, " +
                    "capacity INTEGER NOT NULL, " +
                    "allowedNew INTEGER NOT NULL, " +
                    "amnestyQuota INTEGER NOT NULL, " +
                    "reason TEXT NOT NULL, " +
                    "extraRequested INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, " +
                    "PRIMARY KEY(day))"
            )
        }
    }

    /**
     * v2 -> v3
     *
     * `daily_stats` gains `correctCount`. The day's accuracy used to be stored
     * as an average and edited in place — multiplied back out by the day's
     * total, incremented, divided again, and run backwards by undo. That is
     * float arithmetic in a number the load governor reads, so it is now a
     * count and the average is derived from it.
     *
     * The existing average is rounded back into a count instead of the table
     * being dropped and rebuilt: `daily_stats` is derived and could be
     * regenerated from `reviews`, but doing that during a migration means
     * replaying the entire log on the first launch after an update, and the
     * rounding is exact for every day whose average was still intact.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE daily_stats ADD COLUMN correctCount INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "UPDATE daily_stats SET correctCount = " +
                    "CAST(ROUND(accuracy * reviewsDone) AS INTEGER)"
            )
        }
    }

    /**
     * The nine columns v4 adds to `governor_log`.
     *
     * All defaulted or nullable, so the rows an older version wrote stay valid
     * and simply read as zero -- which is honest: that install did not record
     * the value.
     */
    private val GOVERNOR_LOG_V4 = listOf(
        "ALTER TABLE governor_log ADD COLUMN activityRatio REAL NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN daysSinceStart INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN cleanDays INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN newIntroducedLastWeek INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN totalReviews INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN overheated INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN newCeiling INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE governor_log ADD COLUMN daysSinceReturn INTEGER",
        "ALTER TABLE governor_log ADD COLUMN gate TEXT"
    )

    /**
     * v3 -> v4
     *
     *  - `governor_log` gains the nine signals it was throwing away. The log is
     *    derived and could have been dropped and recreated; ALTER TABLE is used
     *    instead because the rows already there are the only record of why
     *    earlier days were capped, and that record cannot be rebuilt from
     *    `reviews`.
     *  - The full-text index over `chunks` is created and filled. Content is
     *    reinstallable, so this is cheap to get wrong and cheap to repair: see
     *    [ChunkFtsIndex].
     *
     * `reviews` is not touched.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            GOVERNOR_LOG_V4.forEach { db.execSQL(it) }
            ChunkFtsIndex.create(db)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
