package dev.ikna.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// ---------------------------------------------------------------------------
// What version 1 and version 2 of this database actually looked like.
//
// The schema history in app/schemas only starts at version 3: the folder was
// not committed while 1 and 2 were current, and no build of this code can
// produce those files any more. Writing them by hand into app/schemas was the
// obvious move and is the wrong one -- those files are Room's output, and a
// hand-made one with a hand-made identity hash is a lie the next reader has no
// way to check.
//
// So the old shape lives here instead, as the SQL an old install is on, and it
// is not guesswork: both migrations only ADD things. Version 2 is version 3
// without `daily_stats.correctCount`; version 1 is version 2 without
// `daily_plan`, without the `prev*`/`undoOf` columns on `reviews` and its
// index, and without `packs.title` / `packs.isActive`. Reversing an
// append-only history is exact.
//
// SchemaCoverageTest, which runs on the JVM with every build, checks that this
// stays true: version 1 plus what the migrations add has to equal the
// committed version 3 schema, column for column. If somebody adds a field to
// an entity and forgets the migration, that test fails long before a phone
// ever sees it.
// ---------------------------------------------------------------------------

private val V1_DDL = listOf(
    "CREATE TABLE IF NOT EXISTS `chunks` (`id` TEXT NOT NULL, `packId` TEXT NOT NULL, `lang` TEXT NOT NULL, `text` TEXT NOT NULL, `contextSentence` TEXT NOT NULL, `translation` TEXT NOT NULL, `targetStart` INTEGER NOT NULL, `targetEnd` INTEGER NOT NULL, `freqRank` INTEGER NOT NULL, `audioRef` TEXT, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_chunks_freqRank` ON `chunks` (`freqRank`)",
    "CREATE INDEX IF NOT EXISTS `index_chunks_packId` ON `chunks` (`packId`)",
    "CREATE TABLE IF NOT EXISTS `chunk_tokens` (`chunkId` TEXT NOT NULL, `position` INTEGER NOT NULL, `surface` TEXT NOT NULL, `lemma` TEXT NOT NULL, `pos` TEXT NOT NULL, `isTarget` INTEGER NOT NULL, `isContent` INTEGER NOT NULL, `weight` REAL NOT NULL, PRIMARY KEY(`chunkId`, `position`))",
    "CREATE INDEX IF NOT EXISTS `index_chunk_tokens_lemma_pos` ON `chunk_tokens` (`lemma`, `pos`)",
    "CREATE TABLE IF NOT EXISTS `packs` (`id` TEXT NOT NULL, `version` INTEGER NOT NULL, `lang` TEXT NOT NULL, `chunkCount` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `cards` (`chunkId` TEXT NOT NULL, `level` INTEGER NOT NULL, `stability` REAL NOT NULL, `difficulty` REAL NOT NULL, `dueAt` INTEGER NOT NULL, `lastReviewAt` INTEGER, `introducedAt` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `lapses` INTEGER NOT NULL, `inAmnesty` INTEGER NOT NULL, `isNew` INTEGER NOT NULL, PRIMARY KEY(`chunkId`, `level`))",
    "CREATE INDEX IF NOT EXISTS `index_cards_dueAt` ON `cards` (`dueAt`)",
    "CREATE INDEX IF NOT EXISTS `index_cards_inAmnesty` ON `cards` (`inAmnesty`)",
    "CREATE TABLE IF NOT EXISTS `reviews` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chunkId` TEXT NOT NULL, `level` INTEGER NOT NULL, `ts` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `elapsedDays` REAL NOT NULL, `stabilityBefore` REAL NOT NULL, `stabilityAfter` REAL NOT NULL, `difficultyBefore` REAL NOT NULL, `difficultyAfter` REAL NOT NULL, `durationMs` INTEGER NOT NULL, `wasAmnesty` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_reviews_ts` ON `reviews` (`ts`)",
    "CREATE INDEX IF NOT EXISTS `index_reviews_chunkId` ON `reviews` (`chunkId`)",
    "CREATE TABLE IF NOT EXISTS `components` (`lemma` TEXT NOT NULL, `pos` TEXT NOT NULL, `exposures` REAL NOT NULL, `successes` REAL NOT NULL, `stabilityEst` REAL NOT NULL, `firstSeenAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`lemma`, `pos`))",
    "CREATE TABLE IF NOT EXISTS `daily_stats` (`day` TEXT NOT NULL, `reviewsDone` INTEGER NOT NULL, `newIntroduced` INTEGER NOT NULL, `activeMs` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `planCompleted` INTEGER NOT NULL, PRIMARY KEY(`day`))",
    "CREATE TABLE IF NOT EXISTS `governor_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ts` INTEGER NOT NULL, `day` TEXT NOT NULL, `dueToday` INTEGER NOT NULL, `forecastAvg3d` REAL NOT NULL, `backlog` INTEGER NOT NULL, `accuracyRecent` REAL NOT NULL, `daysSinceLastSession` INTEGER NOT NULL, `reviewsDoneToday` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `headroom` REAL NOT NULL, `allowedNew` INTEGER NOT NULL, `reason` TEXT NOT NULL)"
)

private val V2_DDL = listOf(
    "CREATE TABLE IF NOT EXISTS `chunks` (`id` TEXT NOT NULL, `packId` TEXT NOT NULL, `lang` TEXT NOT NULL, `text` TEXT NOT NULL, `contextSentence` TEXT NOT NULL, `translation` TEXT NOT NULL, `targetStart` INTEGER NOT NULL, `targetEnd` INTEGER NOT NULL, `freqRank` INTEGER NOT NULL, `audioRef` TEXT, PRIMARY KEY(`id`))",
    "CREATE INDEX IF NOT EXISTS `index_chunks_freqRank` ON `chunks` (`freqRank`)",
    "CREATE INDEX IF NOT EXISTS `index_chunks_packId` ON `chunks` (`packId`)",
    "CREATE TABLE IF NOT EXISTS `chunk_tokens` (`chunkId` TEXT NOT NULL, `position` INTEGER NOT NULL, `surface` TEXT NOT NULL, `lemma` TEXT NOT NULL, `pos` TEXT NOT NULL, `isTarget` INTEGER NOT NULL, `isContent` INTEGER NOT NULL, `weight` REAL NOT NULL, PRIMARY KEY(`chunkId`, `position`))",
    "CREATE INDEX IF NOT EXISTS `index_chunk_tokens_lemma_pos` ON `chunk_tokens` (`lemma`, `pos`)",
    "CREATE TABLE IF NOT EXISTS `packs` (`id` TEXT NOT NULL, `version` INTEGER NOT NULL, `lang` TEXT NOT NULL, `chunkCount` INTEGER NOT NULL, `installedAt` INTEGER NOT NULL, `title` TEXT, `isActive` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`))",
    "CREATE TABLE IF NOT EXISTS `cards` (`chunkId` TEXT NOT NULL, `level` INTEGER NOT NULL, `stability` REAL NOT NULL, `difficulty` REAL NOT NULL, `dueAt` INTEGER NOT NULL, `lastReviewAt` INTEGER, `introducedAt` INTEGER NOT NULL, `reps` INTEGER NOT NULL, `lapses` INTEGER NOT NULL, `inAmnesty` INTEGER NOT NULL, `isNew` INTEGER NOT NULL, PRIMARY KEY(`chunkId`, `level`))",
    "CREATE INDEX IF NOT EXISTS `index_cards_dueAt` ON `cards` (`dueAt`)",
    "CREATE INDEX IF NOT EXISTS `index_cards_inAmnesty` ON `cards` (`inAmnesty`)",
    "CREATE TABLE IF NOT EXISTS `reviews` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chunkId` TEXT NOT NULL, `level` INTEGER NOT NULL, `ts` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `elapsedDays` REAL NOT NULL, `stabilityBefore` REAL NOT NULL, `stabilityAfter` REAL NOT NULL, `difficultyBefore` REAL NOT NULL, `difficultyAfter` REAL NOT NULL, `durationMs` INTEGER NOT NULL, `wasAmnesty` INTEGER NOT NULL, `prevStability` REAL, `prevDifficulty` REAL, `prevDueAt` INTEGER, `prevLastReviewAt` INTEGER, `prevReps` INTEGER, `prevLapses` INTEGER, `prevIsNew` INTEGER, `prevInAmnesty` INTEGER, `undoOf` INTEGER)",
    "CREATE INDEX IF NOT EXISTS `index_reviews_ts` ON `reviews` (`ts`)",
    "CREATE INDEX IF NOT EXISTS `index_reviews_chunkId` ON `reviews` (`chunkId`)",
    "CREATE INDEX IF NOT EXISTS `index_reviews_undoOf` ON `reviews` (`undoOf`)",
    "CREATE TABLE IF NOT EXISTS `components` (`lemma` TEXT NOT NULL, `pos` TEXT NOT NULL, `exposures` REAL NOT NULL, `successes` REAL NOT NULL, `stabilityEst` REAL NOT NULL, `firstSeenAt` INTEGER NOT NULL, `lastSeenAt` INTEGER NOT NULL, PRIMARY KEY(`lemma`, `pos`))",
    "CREATE TABLE IF NOT EXISTS `daily_stats` (`day` TEXT NOT NULL, `reviewsDone` INTEGER NOT NULL, `newIntroduced` INTEGER NOT NULL, `activeMs` INTEGER NOT NULL, `accuracy` REAL NOT NULL, `planCompleted` INTEGER NOT NULL, PRIMARY KEY(`day`))",
    "CREATE TABLE IF NOT EXISTS `governor_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ts` INTEGER NOT NULL, `day` TEXT NOT NULL, `dueToday` INTEGER NOT NULL, `forecastAvg3d` REAL NOT NULL, `backlog` INTEGER NOT NULL, `accuracyRecent` REAL NOT NULL, `daysSinceLastSession` INTEGER NOT NULL, `reviewsDoneToday` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `headroom` REAL NOT NULL, `allowedNew` INTEGER NOT NULL, `reason` TEXT NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `daily_plan` (`day` TEXT NOT NULL, `plannedIds` TEXT NOT NULL, `plannedTotal` INTEGER NOT NULL, `capacity` INTEGER NOT NULL, `allowedNew` INTEGER NOT NULL, `amnestyQuota` INTEGER NOT NULL, `reason` TEXT NOT NULL, `extraRequested` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`day`))"
)

private const val DB_NAME = "ikna-migration-test.db"

/**
 * The one test in this project that can lose a user their history if it is
 * absent.
 *
 * The review log is the only irreplaceable thing in this app: cards, the word
 * layer and every statistic are derived from it and can be rebuilt, and the log
 * cannot. `fallbackToDestructiveMigration` is banned for exactly that reason,
 * which means a wrong migration does not delete data quietly -- it refuses to
 * open the database at all, on a phone, after the update is installed.
 *
 * So every migration is exercised here against a database that really is on the
 * old schema and really has rows in it. Room does half the work: after the
 * migrations run it validates the result against the schema compiled into the
 * app and throws when a single column disagrees. The rest is checked below --
 * that the answers are still there, and that the derived column the last
 * migration computes comes out right.
 *
 * Instrumented, not a JVM test: it needs the real SQLite that ships with
 * Android, because that is the one the migration will run on. Run it with a
 * phone or an emulator attached:
 *
 *     ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun removeTestDatabase() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `an upgrade from version 1 keeps the answers and gains the new tables`() {
        createOldDatabase(1, V1_DDL) { db ->
            db.execSQL(
                "INSERT INTO reviews (chunkId, level, ts, rating, elapsedDays, " +
                    "stabilityBefore, stabilityAfter, difficultyBefore, " +
                    "difficultyAfter, durationMs, wasAmnesty) VALUES " +
                    "('chunk-1', 0, 1700000000000, 3, 1.5, 2.0, 4.0, 5.0, 5.1, 4200, 0)"
            )
            db.execSQL(
                "INSERT INTO daily_stats (day, reviewsDone, newIntroduced, activeMs, " +
                    "accuracy, planCompleted) VALUES ('2024-01-01', 10, 3, 600000, 0.8, 1)"
            )
            db.execSQL(
                "INSERT INTO packs (id, version, lang, chunkCount, installedAt) " +
                    "VALUES ('en-ru-core', 1, 'en', 500, 1700000000000)"
            )
        }

        withMigratedDatabase { db ->
            assertEquals(
                "The answer written on version 1 is not in the migrated database. " +
                    "The review log is append-only and is the only thing here that " +
                    "cannot be rebuilt from something else.",
                1,
                count(db, "SELECT COUNT(*) FROM reviews WHERE chunkId = 'chunk-1'")
            )
            assertEquals(
                "The rating on that answer changed during the migration.",
                3,
                count(db, "SELECT rating FROM reviews WHERE chunkId = 'chunk-1'")
            )
            assertEquals(
                "correctCount was not derived from the stored average: 0.8 of 10 " +
                    "answers is 8. MIGRATION_2_3 is what computes it, and the load " +
                    "governor reads it to decide whether new material is allowed.",
                8,
                count(db, "SELECT correctCount FROM daily_stats WHERE day = '2024-01-01'")
            )
            assertEquals(
                "packs.isActive did not default to on, so an install that upgrades " +
                    "would come back with every deck switched off.",
                1,
                count(db, "SELECT isActive FROM packs WHERE id = 'en-ru-core'")
            )
            assertEquals(
                "daily_plan is missing or not empty after the upgrade.",
                0,
                count(db, "SELECT COUNT(*) FROM daily_plan")
            )
            assertEquals(
                "undoOf is missing from reviews, so nothing written after the " +
                    "upgrade could ever be taken back.",
                0,
                count(db, "SELECT COUNT(*) FROM reviews WHERE undoOf IS NOT NULL")
            )
        }
    }

    @Test
    fun `an upgrade from version 2 keeps its undo trail`() {
        createOldDatabase(2, V2_DDL) { db ->
            db.execSQL(
                "INSERT INTO reviews (id, chunkId, level, ts, rating, elapsedDays, " +
                    "stabilityBefore, stabilityAfter, difficultyBefore, " +
                    "difficultyAfter, durationMs, wasAmnesty, prevStability, " +
                    "prevDifficulty, prevDueAt, prevReps, prevLapses, prevIsNew, " +
                    "prevInAmnesty) VALUES " +
                    "(1, 'chunk-2', 1, 1700000100000, 1, 0.5, 3.0, 1.0, 6.0, 6.4, 3100, " +
                    "0, 3.0, 6.0, 1700000200000, 4, 1, 0, 0)"
            )
            db.execSQL(
                "INSERT INTO reviews (id, chunkId, level, ts, rating, elapsedDays, " +
                    "stabilityBefore, stabilityAfter, difficultyBefore, " +
                    "difficultyAfter, durationMs, wasAmnesty, undoOf) VALUES " +
                    "(2, 'chunk-2', 1, 1700000110000, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO daily_stats (day, reviewsDone, newIntroduced, activeMs, " +
                    "accuracy, planCompleted) VALUES ('2024-02-02', 4, 1, 200000, 0.5, 0)"
            )
        }

        withMigratedDatabase { db ->
            assertEquals(
                "The retraction row no longer points at the answer it retracts. " +
                    "An undo made before the update would come back as an answer.",
                1,
                count(db, "SELECT undoOf FROM reviews WHERE id = 2")
            )
            assertEquals(
                "The before-state snapshot did not survive the upgrade.",
                4,
                count(db, "SELECT prevReps FROM reviews WHERE id = 1")
            )
            assertEquals(
                "0.5 of 4 answers is 2.",
                2,
                count(db, "SELECT correctCount FROM daily_stats WHERE day = '2024-02-02'")
            )
        }
    }

    @Test
    fun `a fresh install opens at the current version`() {
        withMigratedDatabase { db ->
            assertEquals(3, count(db, "PRAGMA user_version"))
            assertTrue(
                "correctCount is missing from a freshly created database.",
                count(db, "SELECT COUNT(*) FROM daily_stats") == 0
            )
        }
    }

    /**
     * Writes a database that is really on [version], with the tables an install
     * of that version had, and closes it again.
     */
    private fun createOldDatabase(
        version: Int,
        ddl: List<String>,
        seed: (SupportSQLiteDatabase) -> Unit
    ) {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                ddl.forEach { db.execSQL(it) }
                seed(db)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Nothing to do: this helper only ever creates.
            }
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(callback)
                .build()
        )
        // Opening it for writing is what runs onCreate.
        helper.writableDatabase
        helper.close()
    }

    /**
     * Opens the database through Room, which runs every missing migration and
     * then validates the result against the schema compiled into the app. A
     * migration that produces the wrong shape throws here rather than being
     * discovered by a user whose app stopped opening.
     */
    private fun withMigratedDatabase(assertions: (SupportSQLiteDatabase) -> Unit) {
        val room = Room.databaseBuilder(context, IknaDatabase::class.java, DB_NAME)
            .addMigrations(*IknaMigrations.ALL)
            .build()
        try {
            assertions(room.openHelper.writableDatabase)
        } finally {
            room.close()
        }
    }

    private fun count(db: SupportSQLiteDatabase, query: String): Int =
        db.query(query).use { cursor ->
            assertTrue("Query returned no rows: $query", cursor.moveToFirst())
            cursor.getInt(0)
        }
}
