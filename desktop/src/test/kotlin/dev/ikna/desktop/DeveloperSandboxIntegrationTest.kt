package dev.ikna.desktop

import dev.ikna.data.dev.DEVELOPER_DATABASE_FILE
import dev.ikna.data.dev.DeveloperScenario
import dev.ikna.data.dev.IknaDataProfile
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
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

}
