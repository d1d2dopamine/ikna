package dev.ikna.data.db

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.data.repo.StudyStatsRepository
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.DeviceSyncController
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android provider/repositories, isolated fixture only. The foreground test starts an
 * unpaired fixture listener; it never creates an invitation or connects to another device.
 * These are database flow regressions, not a rendered Activity or physical-device acceptance test. */
@RunWith(AndroidJUnit4::class)
class SessionDatabaseFlowsTest {
    private class Fixture {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val name = "session-flows-${UUID.randomUUID()}.db"
        private val home = File(context.cacheDir, "session-flows-${UUID.randomUUID()}").apply { mkdirs() }
        val db = openIknaDatabase(context, name)
        val config = GovernorConfig()
        val components = ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db)
        val learning = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
            db.governorDao(), db.planDao(), db.browseDao(), components,
            Scheduler(dayStartHour = config.dayStartHour), ChunkSelector(), config)
        val stats = StudyStatsRepository(db, config.dayStartHour)
        private val restore = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
            components, Scheduler(dayStartHour = config.dayStartHour), config)
        val controller = DeviceSyncController(File(home, "sync"), restore, onMerged = {})
        val day = learning.dayKey(NOW)

        init {
            learning.syncHistoryBoundary = { controller.undoBoundary() }
            learning.historyBoundaryToken = { controller.boundaryToken() }
            learning.ensureHistoryWritable = { controller.ensureHistoryWritable() }
            restore.historyBoundary = { controller.historyBoundary() }
            restore.journalMergeGuard = { block -> learning.withJournalWriteLock(block) }
        }

        suspend fun seed() {
            db.chunkDao().upsertPack(PackEntity(PACK, 1, "en", 1, NOW, title = "__TECHNICAL_FIXTURE__"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(TARGET, PACK, "en",
                "__TECHNICAL_FIXTURE__", "__TECHNICAL_FIXTURE__", "__TECHNICAL_FIXTURE__", 0, 1, 1)))
            db.chunkDao().insertTokensIgnore(listOf(ChunkTokenEntity(TARGET, 0, TARGET, TARGET, "NOUN", true, true, 1.0)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(PACK, TARGET, 1)))
            db.cardDao().upsertAll(listOf(CardEntity(TARGET, 0, 3.0, 5.0, NOW,
                NOW - DAY_MS, NOW - DAY_MS, reps = 8, isNew = false)))
            db.planDao().upsert(DailyPlanEntity(day, "$TARGET:0", 1, 1, 0, 0, "OK", 0, NOW))
            db.statsDao().upsert(DailyStatEntity(day, 0, 0, 0, 1.0, false))
        }

        suspend fun close() {
            controller.close()
            db.close()
            context.deleteDatabase(name)
            home.deleteRecursively()
        }
    }

    private suspend fun <T> stage(name: String, block: suspend () -> T): T {
        Log.i(TAG, "START $name")
        return withTimeout(10_000) { block() }.also { Log.i(TAG, "PASS $name") }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { stage("seed") { f.seed() }; block(f) } finally { withContext(NonCancellable) { f.close() } }
    }

    @Test(timeout = 60_000) fun inspectionStatisticsAndOfflineSyncToggleComplete() = runBlocking {
        fixture { f ->
            stage("profile initialization") { f.controller.initializeProfileSlot() }
            val before = f.db.planDao().plan(f.day)
            val read = stage("session inspection from Main") {
                withContext(Dispatchers.Main) { f.learning.inspectSession(PACK, NOW) }
            }
            assertEquals(listOf(TARGET), read.plan!!.cards.map { it.chunk.id })
            assertEquals(0, stage("empty statistics") { f.stats.load(NOW) }.week.answers)
            stage("sync enabled without foreground") { f.controller.setEnabled(true) }
            assertTrue(f.controller.state.value.enabled)
            assertFalse(f.controller.state.value.running)
            assertNull(f.controller.state.value.error)
            stage("sync disabled") { f.controller.setEnabled(false) }
            assertFalse(f.controller.state.value.enabled)
            assertFalse(f.controller.state.value.running)
            assertEquals(before, f.db.planDao().plan(f.day))
            assertEquals(0L, f.db.reviewDao().rawCount())
            stage("answer") { f.learning.answer(read.plan!!.cards.single(), Rating.GOOD, 1000, NOW + 1) }
            val after = stage("statistics after answer") { f.stats.load(NOW + 2) }
            assertEquals(1, after.week.answers)
            assertEquals(1, after.week.repeatAnswers)
            assertEquals(1, after.week.targets)
            assertTrue(stage("inspection after answer") { f.learning.inspectSession(PACK, NOW + 2) }.plan!!.cards.isEmpty())
        }
    }

    @Test(timeout = 60_000) fun foregroundSyncToggleStartsAndStopsUnpairedListener() = runBlocking {
        fixture { f ->
            try {
                stage("foreground profile initialization") { f.controller.initializeProfileSlot() }
                stage("sync foreground without enabled transport") { f.controller.setForeground(true) }
                assertFalse(f.controller.state.value.running)
                stage("foreground sync enabled") { f.controller.setEnabled(true) }
                val active = f.controller.state.value
                assertTrue(active.enabled)
                assertTrue(active.running)
                assertNull(active.error)
                assertEquals(0, active.peers)
                assertNull(active.invitation)
            } finally {
                // fixture() closes the controller/database even if disabling or an assertion fails.
                withContext(NonCancellable) {
                    stage("foreground sync disabled") { f.controller.setEnabled(false) }
                    assertFalse(f.controller.state.value.enabled)
                    assertFalse(f.controller.state.value.running)
                }
            }
        }
    }

    @Test(timeout = 60_000) fun concurrentInspectionAndStatisticsReleaseTheirConnections() = runBlocking {
        fixture { f ->
            stage("concurrent reader and writer inspection") {
                coroutineScope {
                    val inspection = async(Dispatchers.Main) { f.learning.inspectSession(PACK, NOW) }
                    val statistics = async(Dispatchers.IO) { f.stats.load(NOW) }
                    assertEquals(1, inspection.await().plan!!.cards.size)
                    assertEquals(0, statistics.await().week.answers)
                }
            }
            assertEquals(1, stage("subsequent inspection") { f.learning.inspectSession(PACK, NOW) }.plan!!.cards.size)
            assertEquals(0L, f.db.reviewDao().rawCount())
        }
    }

    @Test(timeout = 60_000) fun cancelledInspectionReleasesWriterAndPreservesPlan() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val before = f.db.planDao().plan(f.day)
            f.learning.suppressedChunks = { entered.complete(Unit); release.await(); emptySet() }
            val inspection = async(Dispatchers.Main) { f.learning.inspectSession(PACK, NOW) }
            try {
                stage("inspection inside writer") { entered.await() }
                inspection.cancel()
                // Room may own the transaction coroutine. Release before joining the caller.
                release.complete(Unit)
                stage("cancelled inspection exits") { inspection.join() }
                assertTrue(inspection.isCancelled)
                f.learning.suppressedChunks = null
                assertEquals(before, f.db.planDao().plan(f.day))
                assertEquals(0L, f.db.reviewDao().rawCount())
                assertEquals(1, stage("inspection after cancellation") { f.learning.inspectSession(PACK, NOW) }.plan!!.cards.size)
                assertEquals(0, stage("statistics after cancellation") { f.stats.load(NOW) }.week.answers)
            } finally { release.complete(Unit); inspection.cancelAndJoin() }
        }
    }

    @Test(timeout = 60_000) fun cancelledWriterRollsBackAndLeavesStatisticsReadable() = runBlocking {
        fixture { f ->
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val before = requireNotNull(f.db.planDao().plan(f.day))
            val writer = async(Dispatchers.Main) {
                val caller = currentCoroutineContext()
                f.db.inTransaction {
                    f.db.planDao().upsert(before.copy(reason = "__ROLLBACK_FIXTURE__"))
                    entered.complete(Unit)
                    release.await()
                    // Same originating-caller commit gate as production repository transactions.
                    caller.ensureActive()
                }
            }
            try {
                stage("writer changed fixture inside transaction") { entered.await() }
                writer.cancel()
                release.complete(Unit)
                stage("cancelled writer exits") { writer.join() }
                assertTrue(writer.isCancelled)
                assertEquals(before, f.db.planDao().plan(f.day))
                assertEquals(0L, f.db.reviewDao().rawCount())
                assertEquals(0, stage("statistics after rollback") { f.stats.load(NOW) }.week.answers)
                assertEquals(1, stage("inspection after rollback") { f.learning.inspectSession(PACK, NOW) }.plan!!.cards.size)
            } finally { release.complete(Unit); writer.cancelAndJoin() }
        }
    }

    private companion object {
        const val TAG = "SessionDatabaseFlows"
        const val PACK = "session-flow-fixture"
        const val TARGET = "session-flow-target"
        const val DAY_MS = 86_400_000L
        val NOW = LocalDateTime.of(2026, 6, 10, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
