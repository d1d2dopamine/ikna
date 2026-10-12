package dev.ikna.desktop

import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.SettingsBackup
import dev.ikna.data.export.SettingsSnapshot
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BundleWriteSafetyTest {
    private fun fixture(test: suspend (File, File) -> Unit) = runBlocking {
        val home = Files.createTempDirectory("ikna-bundle-write-").toFile()
        val target = File(home, "saved.ikna").apply { writeText("previous backup") }
        try { test(home, target) } finally { home.deleteRecursively() }
    }

    private fun unchanged(home: File, target: File) {
        assertEquals("previous backup", target.readText())
        assertEquals(listOf(target.name), home.listFiles()!!.map { it.name })
    }

    @Test fun midZipFailurePreservesOldDestinationAndCleansTemporaryFile() = fixture { home, target ->
        // These ids collide under the existing safe-name policy, after the first entry was written.
        val failure = runCatching {
            IknaBundle.write(target, emptyList(), "{}", listOf("a/b", "a?b"), false) { "word|context|meaning" }
        }.exceptionOrNull()
        assertTrue(failure is ZipException)
        unchanged(home, target)
    }

    @Test fun deckExportFailurePropagatesAndPreservesPreviousBackup() = fixture { home, target ->
        val expected = IOException("injected deck export failure")
        val failure = runCatching {
            IknaBundle.write(target, emptyList(), "{}", listOf("first", "second"), false) {
                if (it == "second") throw expected
                "word|context|meaning"
            }
        }.exceptionOrNull()
        assertSame(expected, failure)
        unchanged(home, target)
    }

    @Test fun deckCancellationPropagatesAndPreservesPreviousBackup() = fixture { home, target ->
        val expected = CancellationException("injected cancellation")
        val failure = runCatching {
            IknaBundle.write(target, emptyList(), "{}", listOf("first", "second"), false) {
                if (it == "second") throw expected
                "word|context|meaning"
            }
        }.exceptionOrNull()
        assertSame(expected, failure)
        unchanged(home, target)
    }

    @Test fun successfulReplacementIsReadableAndKeepsEmptyHistorySemantics() = fixture { home, target ->
        val result = IknaBundle.write(target, emptyList(), "{}", listOf("full", "empty"), false) {
            if (it == "empty") "" else "word|context|meaning"
        }
        assertEquals(0, result.reviews)
        assertEquals(1, result.decks)
        assertEquals(target.length(), result.bytes)
        ZipFile(target).use { zip ->
            assertEquals("", zip.getInputStream(zip.getEntry("reviews.jsonl")).reader().readText())
            assertEquals("{}", zip.getInputStream(zip.getEntry("settings.json")).reader().readText())
            assertEquals("word|context|meaning", zip.getInputStream(zip.getEntry("decks/full.txt")).reader().readText())
            assertNull(zip.getEntry("decks/empty.txt"))
        }
        assertEquals(listOf(target.name), home.listFiles()!!.map { it.name })
    }
    @Test fun nonSuspendingExporterCancellationIsCheckedBeforePublication() = fixture { home, target ->
        val job = Job()
        val failure = runCatching {
            withContext(job) {
                IknaBundle.write(target, emptyList(), "{}", listOf("deck"), false) {
                    job.cancel()
                    "word|context|meaning"
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        unchanged(home, target)
    }

    @Test fun publicBundleAndPhoneWritersPreserveFormatsAndDeveloperIsolation() = runBlocking {
        val home = Files.createTempDirectory("ikna-export-integration-").toFile()
        val container = DesktopContainer(home, dev.ikna.data.dev.IknaDataProfile.DEVELOPER)
        try {
            requireNotNull(container.developerSandbox).seed(
                dev.ikna.data.dev.DeveloperScenario.EARLY_HISTORY, now = 1_800_000_000_000L
            )
            val expectedReviews = container.db.reviewDao().all()
            assertTrue(expectedReviews.isNotEmpty())
            val target = File(home, "saved.ikna").apply { writeText("old") }
            val result = IknaBundle.write(container, target)
            assertEquals(expectedReviews.size, result.reviews)
            assertEquals(0, result.decks)
            ZipFile(target).use { zip ->
                assertFalse(zip.entries().asSequence().any { it.name.startsWith("decks/") })
                val rows = zip.getInputStream(zip.getEntry("reviews.jsonl")).reader().readLines()
                    .map { ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), it) }
                assertTrue(rows.all { it.synthetic })
                assertEquals(expectedReviews.size, rows.size)
                val settingsText = zip.getInputStream(zip.getEntry("settings.json")).reader().readText()
                assertTrue(ReviewRecord.json.decodeFromString(SettingsSnapshot.serializer(), settingsText).synthetic)
                assertNull(SettingsBackup.decode(settingsText))
            }
            val directory = File(home, "phone")
            val names = IknaBundle.writeForPhone(container, directory, now = 0)
            assertEquals(2, names.size)
            names.forEach { File(directory, it).writeText("old") }
            assertEquals(names, IknaBundle.writeForPhone(container, directory, now = 0))
            val rows = File(directory, names.first { it.endsWith(".jsonl") }).readLines()
                .map { ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), it) }
            assertEquals(expectedReviews.size, rows.size)
            assertTrue(rows.all { it.synthetic })
            val settingsText = File(directory, names.first { it.endsWith(".json") }).readText()
            assertTrue(ReviewRecord.json.decodeFromString(SettingsSnapshot.serializer(), settingsText).synthetic)
            assertNull(SettingsBackup.decode(settingsText))
            assertEquals(names.toSet(), directory.listFiles()!!.map { it.name }.toSet())
        } finally { container.db.close(); home.deleteRecursively() }
    }

}
