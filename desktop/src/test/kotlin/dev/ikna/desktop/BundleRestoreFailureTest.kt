package dev.ikna.desktop

import dev.ikna.data.export.ReviewRecord
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Disposable homes and synthetic journal identifiers only. */
class BundleRestoreFailureTest {
    @Test fun rejectedJournalIsNotReportedAsSuccessfulBundleRestore() = runBlocking {
        val home = Files.createTempDirectory("ikna-dev-bundle-failure-").toFile()
        val container = DesktopContainer(home)
        try {
            val source = home.resolve("unsupported.ikna")
            val row = ReviewRecord(chunkId = "dev-unsupported", ts = 1, rating = 3, gradingVersion = 99)
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("reviews.jsonl"))
                zip.write(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), row).toByteArray())
                zip.closeEntry()
            }
            val failure = runCatching { IknaBundle.read(container, source) }.exceptionOrNull()
            assertTrue("Invalid journal must propagate instead of returning BUNDLE success", failure is IllegalArgumentException)
            assertTrue(container.db.reviewDao().all().isEmpty())
        } finally { container.db.close(); home.deleteRecursively() }
    }

    @Test fun optionalEmptyJournalStillAllowsAnEmptyBundle() = runBlocking {
        val home = Files.createTempDirectory("ikna-dev-bundle-empty-").toFile()
        val container = DesktopContainer(home)
        try {
            val source = home.resolve("empty.ikna")
            ZipOutputStream(source.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("reviews.jsonl")); zip.closeEntry()
            }
            val read = IknaBundle.read(container, source)
            assertEquals(BundleKind.BUNDLE, read.kind)
            assertEquals(0, read.imported)
        } finally { container.db.close(); home.deleteRecursively() }
    }
}
