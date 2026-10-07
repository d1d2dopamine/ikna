package dev.ikna.desktop

import dev.ikna.data.dev.DEVELOPER_DATABASE_FILE
import dev.ikna.data.dev.DeveloperScenario
import dev.ikna.data.dev.IknaDataProfile
import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.session.SessionBuilder
import dev.ikna.domain.session.BrowseUnavailableReason
import dev.ikna.domain.governor.GovernorConfig
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSandboxIntegrationTest {
    @Test
    fun `developer seed uses its own real room database and creates mature history`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-sandbox").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        try {
            val sandbox = requireNotNull(container.developerSandbox)
            val summary = sandbox.seed(DeveloperScenario.MATURE_HISTORY, now = 1_800_000_000_000L)

            assertEquals(DeveloperScenario.MATURE_HISTORY, summary.scenario)
            assertTrue(summary.reviewCount > 0)
            assertTrue(summary.cardCount > 0)
            assertTrue(summary.historyDays >= 20)
            assertTrue(home.resolve(DEVELOPER_DATABASE_FILE).isFile)
            assertFalse(home.resolve("ikna.db").exists())

            val settings = container.settings.current()
            assertTrue(settings.onboardingDone)
            assertFalse(settings.reminderEnabled)
            assertFalse(settings.autoExport)
            assertEquals(DeveloperScenario.MATURE_HISTORY.id, settings.developerScenario)
            assertEquals(summary.reviewCount, container.db.reviewDao().total())
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }
    @Test
    fun `developer profile forces browse past late night and unfinished-plan gates`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-forced-browse").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = LocalDateTime.of(2026, 9, 23, 23, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        try {
            requireNotNull(container.developerSandbox)
                .seed(DeveloperScenario.EARLY_HISTORY, now = now)

            val availability = requireNotNull(
                container.learningRepository
                    .browseDeckAvailability(listOf("developer-reading"), now)["developer-reading"]
            )
            assertTrue(availability.available)
            assertTrue(availability.forcedByDeveloper)
            assertTrue(
                availability.blockers.contains(dev.ikna.domain.session.BrowseUnavailableReason.LATE_NIGHT) ||
                    availability.blockers.contains(dev.ikna.domain.session.BrowseUnavailableReason.PLAN_NOT_COMPLETE)
            )
            assertTrue(availability.remaining > 0)
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `browse ready scenario passes the production browse policy without an override`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-browse").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = LocalDateTime.of(2026, 9, 23, 12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        try {
            requireNotNull(container.developerSandbox)
                .seed(DeveloperScenario.BROWSE_READY, now = now)

            val availability = requireNotNull(
                container.learningRepository
                    .browseDeckAvailability(listOf("developer-reading"), now)["developer-reading"]
            )
            assertTrue(availability.available)
            assertFalse(availability.forcedByDeveloper)
            assertTrue(availability.blockers.isEmpty())
            assertTrue(availability.remaining > 0)
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `product limits switch restores real browse gates and can be turned off again`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-limits").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = LocalDateTime.of(2026, 9, 23, 23, 30)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            requireNotNull(container.developerSandbox).seed(DeveloperScenario.EARLY_HISTORY, now)
            val tools = requireNotNull(container.developerTools)
            tools.applyProductLimits(true)
            val normal = requireNotNull(container.learningRepository
                .browseDeckAvailability(listOf("developer-reading"), now)["developer-reading"])
            assertFalse(normal.available)
            assertFalse(normal.forcedByDeveloper)
            assertTrue(normal.blockers.isNotEmpty())
            tools.applyProductLimits(false)
            val forced = requireNotNull(container.learningRepository
                .browseDeckAvailability(listOf("developer-reading"), now)["developer-reading"])
            assertTrue(forced.available)
            assertTrue(forced.forcedByDeveloper)
            // The normal pool requires familiar, future-due cards. DEV can
            // read existing schedules instead, so NO_CANDIDATES may disappear.
            // Product-policy blockers must still be retained exactly.
            assertEquals(normal.blockers.filterNot { it == BrowseUnavailableReason.NO_CANDIDATES }, forced.blockers)
            assertTrue(forced.blockers.contains(BrowseUnavailableReason.LATE_NIGHT))
            val missing = requireNotNull(container.learningRepository
                .browseDeckAvailability(listOf("missing-deck"), now)["missing-deck"])
            assertFalse("DEV must not fabricate content for a missing deck", missing.available)
            assertFalse(missing.forcedByDeveloper)
            assertTrue(missing.blockers.contains(BrowseUnavailableReason.NO_CANDIDATES))
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `diagnostics refresh cannot create a plan or change history and preferences`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-inspection").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = 1_800_000_000_000L
        try {
            requireNotNull(container.developerSandbox).seed(DeveloperScenario.MATURE_HISTORY, now)
            val day = container.learningRepository.ensureDailyPlan(now).day
            container.db.planDao().clear()
            val history = container.db.reviewDao().total()
            val cards = container.db.cardDao().all()
            val preferences = container.settings.current()
            val first = container.learningRepository.developerDiagnostics(now)
            val second = container.learningRepository.developerDiagnostics(now)
            assertEquals(first, second)
            assertEquals(null, first.planReason)
            assertEquals(0, first.planSize)
            assertEquals(history, container.db.reviewDao().total())
            assertEquals(cards, container.db.cardDao().all())
            assertEquals(preferences, container.settings.current())
            assertEquals(null, container.db.planDao().plan(day))
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `retired modes survive storage but not materialization or daily plans`() = runBlocking {
        val home = Files.createTempDirectory("ikna-classic-history").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = 1_800_000_000_000L
        try {
            requireNotNull(container.developerSandbox).seed(DeveloperScenario.MATURE_HISTORY, now)
            val dao = container.db.cardDao()
            val classic = dao.all().first()
            val retired = listOf(classic.copy(level = 1), classic.copy(level = 2))
            dao.upsertAll(retired)
            val keys = listOf(classic.key) + retired.map { it.key }
            val builder = SessionBuilder(dao, container.db.chunkDao(), GovernorConfig())
            assertEquals(listOf(classic.key), builder.materialize(keys).map { it.card.key })
            val day = requireNotNull(container.learningRepository.ensureDailyPlan(now)).day
            val mixed = DailyPlanEntity(day, keys.joinToString(","), 3, 40, 5, 0, "OK", 1, now)
            container.db.planDao().upsert(mixed)
            val history = container.db.reviewDao().total()
            val active = container.learningRepository.ensureDailyPlan(now)
            assertEquals(listOf(classic.key), active.ids)
            assertEquals(mixed.capacity, active.capacity)
            assertEquals(mixed.allowedNew, active.allowedNew)
            assertEquals(0, active.extraRequested)
            assertEquals(history, container.db.reviewDao().total())
            assertEquals(retired[0], dao.card(classic.chunkId, 1))
            assertEquals(retired[1], dao.card(classic.chunkId, 2))
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `classic answer keeps scheduling and undo without creating another mode`() = runBlocking {
        val home = Files.createTempDirectory("ikna-classic-answer").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = 1_800_000_000_000L
        try {
            requireNotNull(container.developerSandbox).seed(DeveloperScenario.MATURE_HISTORY, now)
            val plan = container.learningRepository.ensureDailyPlan(now)
            // A mature card with ample allowance used to trigger promotion.
            container.db.planDao().upsert(plan.copy(allowedNew = 100))
            container.db.statsDao().day(plan.day)?.let { stat ->
                container.db.statsDao().upsert(stat.copy(newIntroduced = 0))
            }
            val dao = container.db.cardDao()
            val before = dao.all().first().copy(stability = 100.0, reps = 30, isNew = false)
            dao.upsert(before)
            val card = SessionBuilder(dao, container.db.chunkDao(), GovernorConfig())
                .materialize(listOf(before.key)).single()
            val history = container.db.reviewDao().total()
            val rawRows = container.db.reviewDao().observeOptimizerChanges().first()
            container.learningRepository.answer(card, Rating.GOOD, 4200L, now)
            assertEquals(history + 1, container.db.reviewDao().total())
            assertEquals(before.reps + 1, requireNotNull(dao.card(before.chunkId, 0)).reps)
            assertEquals(null, dao.card(before.chunkId, 1))
            assertEquals(null, dao.card(before.chunkId, 2))
            assertTrue(container.learningRepository.undoLast(now + 1_000L) != null)
            assertEquals(before, dao.card(before.chunkId, 0))
            // total() counts non-retracted answers; undo restores that count.
            // The raw journal still appends both the answer and its retraction.
            assertEquals(history, container.db.reviewDao().total())
            assertEquals(rawRows + 2L, container.db.reviewDao().observeOptimizerChanges().first())
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

    @Test
    fun `DEV can reopen scheduled cards without changing a completed daily obligation`() = runBlocking {
        val home = Files.createTempDirectory("ikna-developer-card-access").toFile()
        val container = DesktopContainer(home, IknaDataProfile.DEVELOPER)
        val now = 1_800_000_000_000L
        try {
            requireNotNull(container.developerSandbox).seed(DeveloperScenario.MATURE_HISTORY, now)
            val plan = container.learningRepository.ensureDailyPlan(now)
                .copy(plannedIds = "", plannedTotal = 0, extraRequested = 0)
            container.db.planDao().upsert(plan)
            val history = container.db.reviewDao().total()
            val forced = container.learningRepository.buildSession(now = now)
            assertTrue(forced.forcedByDeveloper)
            assertTrue(forced.cards.isNotEmpty())
            assertTrue(forced.cards.all { it.card.level == 0 })
            assertEquals(plan, container.db.planDao().plan(plan.day))
            assertEquals(history, container.db.reviewDao().total())
            requireNotNull(container.developerTools).applyProductLimits(true)
            val normal = container.learningRepository.buildSession(now = now)
            assertFalse(normal.forcedByDeveloper)
            assertTrue(normal.cards.isEmpty())
            assertEquals(plan, container.db.planDao().plan(plan.day))
        } finally {
            container.db.close()
            home.deleteRecursively()
        }
    }

}
