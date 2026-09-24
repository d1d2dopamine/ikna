package dev.ikna.data.dev

import dev.ikna.domain.session.BrowseAvailability
import dev.ikna.domain.session.BrowseUnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DeveloperModeTest {
    @Test
    fun `profile store defaults real and persists developer separately`() {
        val dir = Files.createTempDirectory("ikna-profile-test").toFile()
        try {
            val store = DataProfileStore(dir.resolve(DATA_PROFILE_FILE))
            assertEquals(IknaDataProfile.REAL, store.current())
            store.set(IknaDataProfile.DEVELOPER)
            assertEquals(IknaDataProfile.DEVELOPER, DataProfileStore(dir.resolve(DATA_PROFILE_FILE)).current())
            assertEquals("ikna-developer.db", databaseFileName(IknaDataProfile.DEVELOPER))
            assertEquals("ikna.db", databaseFileName(IknaDataProfile.REAL))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `developer profile always enables forced product access`() {
        assertFalse(DeveloperAccess.forProfile(IknaDataProfile.REAL).active)
        assertTrue(DeveloperAccess.forProfile(IknaDataProfile.DEVELOPER).active)
    }

    @Test
    fun `bad bootstrap value fails closed to real profile`() {
        val dir = Files.createTempDirectory("ikna-profile-invalid").toFile()
        try {
            val file = dir.resolve(DATA_PROFILE_FILE)
            file.writeText("SOMETHING_ELSE")
            assertEquals(IknaDataProfile.REAL, DataProfileStore(file).current())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `developer forced availability keeps production blockers visible`() {
        val blocked = BrowseAvailability(
            remaining = 20,
            reason = BrowseUnavailableReason.LATE_NIGHT,
            additionalReasons = listOf(BrowseUnavailableReason.NO_CREDITS),
            forcedByDeveloper = true
        )
        assertTrue(blocked.available)
        assertEquals(
            listOf(BrowseUnavailableReason.LATE_NIGHT, BrowseUnavailableReason.NO_CREDITS),
            blocked.blockers
        )

        val production = blocked.copy(forcedByDeveloper = false)
        assertFalse(production.available)
    }

    @Test
    fun `scenario ids are stable and unknown values become empty`() {
        DeveloperScenario.entries.forEach { scenario ->
            assertEquals(scenario, DeveloperScenario.fromId(scenario.id))
        }
        assertEquals(DeveloperScenario.EMPTY, DeveloperScenario.fromId("future-value"))
    }
}
