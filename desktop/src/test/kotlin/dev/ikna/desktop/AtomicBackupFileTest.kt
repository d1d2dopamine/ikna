package dev.ikna.desktop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class AtomicBackupFileTest {
    private fun fixture(test: suspend (File, File) -> Unit) = runBlocking {
        val home = Files.createTempDirectory("ikna-atomic-backup-").toFile()
        val target = File(home, "saved.json").apply { writeText("old") }
        try { test(home, target) } finally { home.deleteRecursively() }
    }

    private fun unchanged(home: File, target: File) {
        assertEquals("old", target.readText())
        assertEquals(listOf(target.name), home.listFiles()!!.map { it.name })
    }

    @Test fun partialWriterFailurePreservesOldFileAndCleansTemporaryFile() = fixture { home, target ->
        val expected = IOException("injected disk failure")
        val failure = runCatching {
            writeBackupAtomically(target) { temporary ->
                temporary.outputStream().use { it.write("partial".toByteArray()); throw expected }
            }
        }.exceptionOrNull()
        assertSame(expected, failure)
        unchanged(home, target)
    }

    @Test fun cancellationAfterWriterClosesIsCheckedBeforePublication() = fixture { home, target ->
        val job = Job()
        val failure = runCatching {
            withContext(job) {
                writeBackupAtomically(target) { temporary -> temporary.writeText("new"); job.cancel() }
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        unchanged(home, target)
    }

    @Test fun preCancelledWriterDoesNotStart() = fixture { home, target ->
        val job = Job()
        var started = false
        val failure = runCatching {
            withContext(job) {
                job.cancel()
                writeBackupAtomically(target) { started = true }
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(started)
        unchanged(home, target)
    }

    @Test fun failedFirstSaveDoesNotLeaveAnApparentBackup() = fixture { home, target ->
        val fresh = File(home, "new.json")
        val failure = runCatching {
            writeBackupAtomically(fresh) { it.writeText("partial"); throw IOException("failed") }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(fresh.exists())
        unchanged(home, target)
    }

    @Test fun failedPublicationLeavesExistingDirectoryUntouchedAndCleansTemp() = fixture { home, target ->
        val blocked = File(home, "blocked").apply { mkdir() }
        val marker = File(blocked, "marker").apply { writeText("keep") }
        val failure = runCatching { writeBackupAtomically(blocked) { it.writeText("new") } }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("keep", marker.readText())
        assertEquals(setOf(target.name, blocked.name), home.listFiles()!!.map { it.name }.toSet())
    }

    @Test fun successfulReplacementUsesOnlyCompleteNewBytes() = fixture { home, target ->
        writeBackupAtomically(target) { it.writeText("new") }
        assertEquals("new", target.readText())
        assertEquals(listOf(target.name), home.listFiles()!!.map { it.name })
    }
}
