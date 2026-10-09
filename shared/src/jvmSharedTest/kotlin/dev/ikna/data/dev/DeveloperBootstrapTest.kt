package dev.ikna.data.dev

import java.nio.file.Files
import kotlin.test.*

class DeveloperBootstrapTest {
    @Test
    fun repeatedProfileSwitchesReplaceTheExistingFileWithoutChangingOtherData() {
        val dir = Files.createTempDirectory("ikna-profile").toFile()
        try {
            val real = dir.resolve(REAL_DATABASE_FILE).apply { writeText("learner sentinel") }
            val dev = dir.resolve(DEVELOPER_DATABASE_FILE).apply { writeText("sandbox sentinel") }
            val file = dir.resolve(DATA_PROFILE_FILE)
            assertEquals(IknaDataProfile.REAL, DataProfileStore(file).current())
            repeat(6) {
                DataProfileStore(file).set(IknaDataProfile.DEVELOPER)
                assertEquals(IknaDataProfile.DEVELOPER, DataProfileStore(file).current())
                DataProfileStore(file).set(IknaDataProfile.REAL)
                assertEquals(IknaDataProfile.REAL, DataProfileStore(file).current())
            }
            assertEquals("learner sentinel", real.readText())
            assertEquals("sandbox sentinel", dev.readText())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun confirmedScenarioRequestsPersistAndClearOnlyAfterAcceptance() {
        val dir = Files.createTempDirectory("ikna-scenario-request").toFile()
        try {
            val file = dir.resolve(DEVELOPER_SCENARIO_REQUEST_FILE)
            val store = PendingDeveloperScenarioStore(file)
            assertNull(store.current())
            for (scenario in DeveloperScenario.entries) {
                store.set(scenario)
                assertEquals(scenario, PendingDeveloperScenarioStore(file).current())
            }
            store.clear()
            store.clear()
            assertNull(store.current())
            file.writeText("invalid-scenario")
            assertFailsWith<IllegalStateException> { store.current() }
            assertEquals("invalid-scenario", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun failedReplacementDoesNotLeavePartialBootstrapFiles() {
        val dir = Files.createTempDirectory("ikna-bad-bootstrap").toFile()
        try {
            val destination = dir.resolve(DATA_PROFILE_FILE).apply { mkdir() }
            destination.resolve("keep").writeText("sentinel")
            assertFails { DataProfileStore(destination).set(IknaDataProfile.DEVELOPER) }
            assertEquals("sentinel", destination.resolve("keep").readText())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }
}
