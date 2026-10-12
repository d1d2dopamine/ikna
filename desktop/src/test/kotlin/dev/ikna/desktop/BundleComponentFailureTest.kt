package dev.ikna.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.data.dev.databaseFileName
import dev.ikna.data.prefs.SETTINGS_DATASTORE_FILE
import dev.ikna.data.prefs.ThemeMode
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Public restore entry point, disposable homes and unmistakable technical fixtures only. */
class BundleComponentFailureTest {
    @Test fun deckWriteFailureCannotReturnSuccessfulBundle() = runBlocking {
        inHome { home, container ->
            container.db.chunkDao().packs() // Create the schema before adding the fault trigger.
            BundledSQLiteDriver().open(home.resolve(databaseFileName(container.dataProfile)).path).use { db ->
                db.prepare("CREATE TRIGGER reject_fixture_pack BEFORE INSERT ON packs " +
                    "BEGIN SELECT RAISE(ABORT, 'disposable pack write failure'); END").use { it.step() }
            }
            val source = bundle(home, "decks/dev-fixture.txt" to DECK)
            val failure = runCatching { IknaBundle.read(container, source) }.exceptionOrNull()
            assertTrue("The injected deck write failure must escape the bundle reader",
                generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("disposable pack write failure") })
            assertTrue(container.db.chunkDao().packs().isEmpty())
            // Import is not a whole-bundle transaction: earlier chunk writes may persist.
            assertTrue(container.db.chunkDao().chunksForPack("user-dev-fixture").isNotEmpty())
            container.learningRepository.invalidatePlan() // The injected failure is not a broken database.
        }
    }

    @Test fun settingsWriteFailureCannotReturnSuccessfulBundle() = runBlocking {
        inHome(blockSettings = true) { home, container ->
            val source = bundle(home, "settings.json" to SETTINGS)
            val failure = runCatching { IknaBundle.read(container, source) }.exceptionOrNull()
            assertTrue("A settings storage failure must escape the bundle reader", failure is IOException)
            container.learningRepository.invalidatePlan()
        }
    }

    @Test fun settingsWriteFailureCannotBecomeUnknownSingleFile() = runBlocking {
        inHome(blockSettings = true) { home, container ->
            val source = home.resolve("settings.json").apply { writeText(SETTINGS) }
            val failure = runCatching { IknaBundle.read(container, source) }.exceptionOrNull()
            assertTrue("A recognized settings file must expose its storage failure", failure is IOException)
        }
    }

    @Test fun settingsCancellationEscapesWithoutBeingTurnedIntoSkip() = runBlocking {
        inHome { home, container ->
            val cancellation = CancellationException("disposable settings cancellation")
            container.settings.onOptimizerReset = { throw cancellation }
            val source = bundle(home, "settings.json" to SETTINGS)
            assertSame(cancellation, runCatching { IknaBundle.read(container, source) }.exceptionOrNull())
        }
    }

    @Test fun optionalBlankAndUndecodableSettingsRemainSkippable() = runBlocking {
        inHome { home, container ->
            for (settings in listOf("", "not json", "{}", "{\"kind\":\"other\"}")) {
                val read = IknaBundle.read(container, bundle(home,
                    "reviews.jsonl" to "", "decks/dev-blank.txt" to " \n", "settings.json" to settings))
                assertEquals(BundleKind.BUNDLE, read.kind)
                assertEquals(0, read.decks)
                assertEquals(0, read.imported)
                assertFalse(read.settings)
            }
            assertTrue(container.db.chunkDao().packs().isEmpty())
        }
    }

    @Test fun syntheticSettingsRemainRejectedWithoutChangingRealSettings() = runBlocking {
        inHome { home, container ->
            container.settings.setTheme(ThemeMode.CUSTOM)
            val read = IknaBundle.read(container, bundle(home, "settings.json" to
                "{\"kind\":\"ikna-settings\",\"synthetic\":true,\"theme\":\"DARK\"}"))
            assertEquals(BundleKind.BUNDLE, read.kind)
            assertFalse(read.settings)
            assertEquals(ThemeMode.CUSTOM, container.settings.current().theme)
        }
    }

    @Test fun validDeckAndFutureVersionSettingsStillRestore() = runBlocking {
        inHome { home, container ->
            val read = IknaBundle.read(container, bundle(home,
                "decks/dev-fixture.txt" to DECK,
                "settings.json" to "{\"kind\":\"ikna-settings\",\"version\":9,\"theme\":\"CUSTOM\",\"futureField\":42}"))
            assertEquals(BundleKind.BUNDLE, read.kind)
            assertEquals(1, read.decks)
            assertTrue(read.settings)
            assertEquals(1, container.db.chunkDao().chunksForPack("user-dev-fixture").size)
            assertEquals(ThemeMode.CUSTOM, container.settings.current().theme)
        }
    }

    private suspend fun inHome(
        blockSettings: Boolean = false,
        block: suspend (File, DesktopContainer) -> Unit
    ) {
        val home = Files.createTempDirectory("ikna-dev-bundle-components-").toFile()
        if (blockSettings) check(home.resolve(SETTINGS_DATASTORE_FILE).mkdir())
        val container = DesktopContainer(home)
        try { block(home, container) }
        finally { container.db.close(); home.deleteRecursively() }
    }

    private fun bundle(home: File, vararg entries: Pair<String, String>): File =
        home.resolve("fixture.ikna").also { target ->
            ZipOutputStream(target.outputStream()).use { zip ->
                for ((name, body) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(body.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }

    private companion object {
        const val DECK = "dev-target-A|dev-context dev-target-A|dev-meaning-A"
        const val SETTINGS = "{\"kind\":\"ikna-settings\",\"theme\":\"CUSTOM\"}"
    }
}
