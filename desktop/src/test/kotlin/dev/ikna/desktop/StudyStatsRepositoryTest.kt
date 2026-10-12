package dev.ikna.desktop

import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.repo.StudyStatsRepository
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Synthetic journal rows only; never opens a learner profile. */
class StudyStatsRepositoryTest {
    private val now = Instant.parse("2026-10-12T12:00:00Z").toEpochMilli()
    private fun row(target: String, daysAgo: Int, rating: Int = 3, previousNew: Boolean? = false,
        undoOf: Long? = null) = ReviewEntity(chunkId = target, level = 0,
        ts = now - daysAgo * 86_400_000L, rating = rating, elapsedDays = 1.0,
        stabilityBefore = 1.0, stabilityAfter = 2.0, difficultyBefore = 5.0,
        difficultyAfter = 5.0, durationMs = 0, wasAmnesty = false,
        prevIsNew = previousNew, undoOf = undoOf)

    @Test fun journalCountsExcludeUndoAndFutureAndRespectGlobalIdentity() = runBlocking {
        val home = Files.createTempDirectory("ikna-dev-stats-").toFile()
        val db = openIknaDatabase(home.resolve("disposable.db"))
        try {
            val dao = db.reviewDao()
            val old = dao.insert(row("dev-a", 10, previousNew = true))
            dao.insert(row("dev-a", 2))
            dao.insert(row("dev-a", 1))
            dao.insert(row("dev-b", 1, previousNew = null))
            dao.insert(row("dev-future", -1))
            val repo = StudyStatsRepository(db)
            val first = repo.load(now, ZoneId.of("UTC"))
            assertEquals(2, first.week.targets)
            assertEquals(1, first.week.firstRecordedTargets)
            assertEquals(2, first.month.firstRecordedTargets)
            assertEquals(2, first.week.repeatAnswers)
            assertEquals(1, first.week.unclassifiedAnswers)
            dao.insert(row("dev-a", 0, rating = 0, undoOf = old))
            val before = dao.all()
            val afterUndo = repo.load(now, ZoneId.of("UTC"))
            assertEquals(2, afterUndo.week.firstRecordedTargets)
            assertEquals(3, afterUndo.week.answers)
            assertEquals(before, dao.all()) // Statistics is read-only, including its transaction.
        } finally { db.close(); home.deleteRecursively() }
    }

    @Test fun actualDaoExcludesRetractedMissFromPatternsAndKeepsMissingContent() = runBlocking {
        val home = Files.createTempDirectory("ikna-dev-stats-pattern-").toFile()
        val db = openIknaDatabase(home.resolve("disposable.db"))
        try {
            val dao = db.reviewDao()
            val miss = dao.insert(row("dev-a", 3, rating = 1))
            dao.insert(row("dev-a", 2)); dao.insert(row("dev-a", 1))
            val repo = StudyStatsRepository(db)
            val before = repo.load(now, ZoneId.of("UTC"))
            assertEquals(1, before.week.recovered.size)
            assertEquals("", before.week.recovered.single().text)
            dao.insert(row("dev-a", 0, rating = 0, undoOf = miss))
            assertTrue(repo.load(now, ZoneId.of("UTC")).week.recovered.isEmpty())
        } finally { db.close(); home.deleteRecursively() }
    }
}
