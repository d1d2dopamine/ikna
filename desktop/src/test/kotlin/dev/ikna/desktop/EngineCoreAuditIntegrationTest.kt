package dev.ikna.desktop

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.PackChunkEntity
import dev.ikna.data.db.PackEntity
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.ComponentKey
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.SessionBuilder
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real Room integration tests for the Phase 1 & Phase 2 engine refactor. */
class EngineCoreAuditIntegrationTest {

    private class Fixture {
        val home = Files.createTempDirectory("ikna-engine-audit-").toFile()
        val file = home.resolve("profile.db")
        val db = openIknaDatabase(file)
        var hidden = emptySet<String>()
        val config = GovernorConfig(earnedNewPerReviews = 4)
        val components = ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db)
        fun repo() = LearningRepository(
            db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
            db.governorDao(), db.planDao(), db.browseDao(),
            components, Scheduler(dayStartHour = config.dayStartHour), ChunkSelector(), config
        ).also { it.suppressedChunks = { hidden } }
        fun close() { db.close(); home.deleteRecursively() }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.close() }
    }

    @Test
    fun `repeated AGAIN lapses never earn new cards while recalled reviews still do`() = runBlocking {
        fixture { f ->
            f.db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 3, NOW))
            val ids = listOf("req", "fresh-1", "fresh-2")
            f.db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id ->
                ChunkEntity(id, "pack", "en", id, "context $id", "tr $id", 0, id.length, i)
            })
            f.db.chunkDao().insertTokensIgnore(ids.map { id ->
                ChunkTokenEntity(id, 0, id, id, "NOUN", true, true, 1.0)
            })
            f.db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity("pack", id, i) })
            f.db.cardDao().upsert(CardEntity("req", 0, 3.0, 5.0, NOW, NOW - DAY_MS, NOW - 2 * DAY_MS, reps = 3, isNew = false))
            f.db.planDao().upsert(DailyPlanEntity(DAY, "req:0", 1, 40, 1, 0, "NO_HEADROOM", 0, NOW - 1000))
            f.db.statsDao().upsert(DailyStatEntity(DAY, 0, 1, 0L, 1.0, false, 0))

            val repo = f.repo()
            val card = SessionBuilder(f.db.cardDao(), f.db.chunkDao(), f.config).materialize(listOf("req:0")).single()

            // Failing 6 times in a row must NEVER reward the learner with a new card.
            for (i in 0 until 6) {
                repo.answer(card, Rating.AGAIN, 1500L, NOW + i * 1000L)
            }
            assertNull("Repeated AGAIN must not introduce fresh-1", f.db.cardDao().card("fresh-1", 0))
            assertNull("Repeated AGAIN must not introduce fresh-2", f.db.cardDao().card("fresh-2", 0))

            // 5 recalled GOOD answers (1 obligation + 4 beyond) still earn 1 new card.
            for (i in 0 until 5) {
                repo.answer(card, Rating.GOOD, 1500L, NOW + 10_000L + i * 1000L)
            }
            assertNotNull("Recalled answers beyond obligation should earn a new card", f.db.cardDao().card("fresh-1", 0))
        }
    }

    @Test
    fun `resetProgressWithBoundary isolates live governor signals and daily plan from pre-reset reviews`() = runBlocking {
        fixture { f ->
            f.db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 3, NOW))
            val ids = listOf("c1", "c2", "c3")
            f.db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id ->
                ChunkEntity(id, "pack", "en", id, "sentence $id", "meaning $id", 0, id.length, i)
            })
            f.db.chunkDao().insertTokensIgnore(ids.map { id ->
                ChunkTokenEntity(id, 0, id, id, "NOUN", true, true, 1.0)
            })
            f.db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity("pack", id, i) })

            val repo = f.repo()
            val initialSession = repo.buildSession(now = NOW - 14 * DAY_MS)
            assertTrue(initialSession.cards.isNotEmpty())
            for ((idx, card) in initialSession.cards.withIndex()) {
                repo.answer(card, Rating.AGAIN, 2000L, NOW - 14 * DAY_MS + idx * 1000L)
            }

            // Reset progress today — archived rows remain in `reviews`, but boundary is recorded.
            repo.resetProgressWithBoundary()

            val signals = repo.collectSignals(NOW)
            assertEquals("Pre-reset reviews must not leak into totalReviews", 0, signals.totalReviews)
            assertEquals("Pre-reset timestamps must not trigger absence gap", 0, signals.daysSinceLastSession)
            assertEquals("Pre-reset failures must not tank post-reset accuracy", 1.0, signals.accuracyRecent, 1e-9)

            val freshPlan = repo.ensureDailyPlan(NOW)
            assertEquals(GovernorReason.FIRST_RUN.name, freshPlan.reason)
            assertTrue("Fresh post-reset plan should introduce cards cleanly", freshPlan.plannedTotal > 0)
        }
    }

    @Test
    fun `remainingByDeck and pickForDay exclude suppressed chunks cleanly`() = runBlocking {
        fixture { f ->
            f.db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 3, NOW))
            val ids = listOf("keep-1", "bad-1", "keep-2")
            f.db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id ->
                ChunkEntity(id, "pack", "en", id, "sentence $id", "meaning $id", 0, id.length, i)
            })
            f.db.chunkDao().insertTokensIgnore(ids.map { id ->
                ChunkTokenEntity(id, 0, id, id, "NOUN", true, true, 1.0)
            })
            f.db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity("pack", id, i) })
            f.db.cardDao().upsertAll(ids.map { id ->
                CardEntity(id, 0, 3.0, 5.0, NOW - 1000L, NOW - 2 * DAY_MS, NOW - 5 * DAY_MS, reps = 2, isNew = false)
            })
            f.db.planDao().upsert(DailyPlanEntity(DAY, "keep-1:0,bad-1:0,keep-2:0", 3, 40, 0, 0, "OK", 0, NOW - 1000))

            f.hidden = setOf("bad-1")
            val repo = f.repo()
            assertEquals(mapOf("pack" to 2), repo.remainingByDeck(NOW))
            assertEquals(2, repo.todayStudySummary(NOW).remaining)
        }
    }

    @Test
    fun `ComponentRepository respects spacing effect and isolates languages`() = runBlocking {
        fixture { f ->
            f.db.chunkDao().upsertPack(PackEntity("p-en", 1, "en", 1, NOW))
            f.db.chunkDao().upsertPack(PackEntity("p-de", 1, "de", 1, NOW))
            f.db.chunkDao().insertChunksIgnore(listOf(
                ChunkEntity("en-gift", "p-en", "en", "gift", "A gift", "Подарок", 2, 6, 1),
                ChunkEntity("de-gift", "p-de", "de", "Gift", "Das Gift", "Яд", 4, 8, 1)
            ))
            f.db.chunkDao().insertTokensIgnore(listOf(
                ChunkTokenEntity("en-gift", 0, "gift", "gift", "NOUN", true, true, 1.0),
                ChunkTokenEntity("de-gift", 0, "Gift", "gift", "NOUN", true, true, 1.0)
            ))

            // 10 massed repetitions of en-gift within 5 minutes must NOT inflate stability to hundreds of days.
            for (i in 0 until 10) {
                f.components.recordAnswer("en-gift", Rating.GOOD.value, NOW + i * 30_000L)
            }
            val massed = f.components.all()[ComponentKey.forLang("en", "gift", "NOUN")]
            assertNotNull(massed)
            assertTrue("Massed same-session repeats must not inflate stability; got ${massed!!.stabilityEst}", massed.stabilityEst < 3.0)

            // German "Gift" must remain completely separate from English "gift".
            assertNull(f.components.all()[ComponentKey.forLang("de", "gift", "NOUN")])

            // Spaced multi-day repetitions of de-gift grow stability significantly higher than massed practice.
            var t = NOW
            for (i in 0 until 6) {
                f.components.recordAnswer("de-gift", Rating.GOOD.value, t)
                t += (i + 1) * 2L * DAY_MS
            }
            val spaced = f.components.all()[ComponentKey.forLang("de", "gift", "NOUN")]
            assertNotNull(spaced)
            assertTrue(
                "Spaced multi-day practice (${spaced!!.stabilityEst}) must strongly exceed massed practice (${massed.stabilityEst})",
                spaced.stabilityEst > massed.stabilityEst * 4.0
            )
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private const val DAY = "2026-10-12"
    }
}
