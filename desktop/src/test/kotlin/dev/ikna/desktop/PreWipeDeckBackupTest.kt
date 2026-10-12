package dev.ikna.desktop

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PreWipeDeckBackupTest {
    private fun fixture(test: (File) -> Unit) {
        val directory = Files.createTempDirectory("ikna-wipe-test-").toFile()
        try { test(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun duplicateNamesAndEmptyDecksAreSavedBeforeWipe() = fixture { home -> runBlocking {
        var wipes = 0
        val directory = backupDeckTextsThenWipe(
            File(home, "export"), { listOf("first", "second", "") }, { "same.txt" }, { it },
            {
                val saved = File(home, "export").listFiles()!!.single().listFiles()!!
                assertEquals(listOf("", "first", "second"), saved.map { it.readText() }.sorted())
                wipes++
            }
        )
        assertTrue(directory.isDirectory)
        assertEquals(1, wipes)
    } }

    @Test fun emptyInventoryIsValid() = fixture { home -> runBlocking {
        var wiped = false
        backupDeckTextsThenWipe(File(home, "export"), { emptyList<String>() }, { it }, { it }, { wiped = true })
        assertTrue(wiped)
    } }

    @Test fun blockedExportDirectoryBlocksWipeAndRetryCanSucceed() = fixture { home -> runBlocking {
        val root = File(home, "export").apply { writeText("disposable blocker") }
        var wipes = 0
        val failure = runCatching {
            backupDeckTextsThenWipe(root, { listOf("deck") }, { "deck.txt" }, { it }, { wipes++ })
        }.exceptionOrNull()
        assertTrue(failure is PreWipeDeckBackupException)
        assertEquals(0, wipes)
        assertTrue(root.delete())
        backupDeckTextsThenWipe(root, { listOf("deck") }, { "deck.txt" }, { it }, { wipes++ })
        assertEquals(1, wipes)
    } }

    @Test fun failureAfterOneDeckKeepsAllDataAndRetryDoesNotOverwrite() = fixture { home -> runBlocking {
        val root = File(home, "export")
        var wipes = 0
        val failure = runCatching {
            backupDeckTextsThenWipe(root, { listOf("first", "second") }, { "same.txt" },
                { if (it == "second") throw IOException("read failed"); it }, { wipes++ })
        }.exceptionOrNull()
        assertTrue(failure is PreWipeDeckBackupException)
        assertEquals(0, wipes)
        val partial = root.listFiles()!!.single()
        backupDeckTextsThenWipe(root, { listOf("first", "second") }, { "same.txt" }, { it }, { wipes++ })
        assertEquals(2, root.listFiles()!!.size)
        assertEquals("first", partial.listFiles()!!.single().readText())
        assertEquals(1, wipes)
    } }

    @Test fun inventoryReadFailureBlocksWipe() = fixture { home -> runBlocking {
        var wiped = false
        val failure = runCatching {
            backupDeckTextsThenWipe<String>(File(home, "export"), { throw IOException("read failed") },
                { it }, { it }, { wiped = true })
        }.exceptionOrNull()
        assertTrue(failure is PreWipeDeckBackupException)
        assertFalse(wiped)
    } }

    @Test fun cancellationBlocksWipeWithoutWrapping() = fixture { home -> runBlocking {
        var wiped = false
        val failure = runCatching {
            backupDeckTextsThenWipe(File(home, "export"), { listOf("deck") }, { "deck.txt" },
                { throw CancellationException("cancelled") }, { wiped = true })
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(wiped)
    } }

    @Test fun wipeFailureIsNotMislabelledAsBackupFailure() = fixture { home -> runBlocking {
        val expected = IOException("wipe failed")
        val failure = runCatching {
            backupDeckTextsThenWipe(File(home, "export"), { listOf("deck") }, { "deck.txt" },
                { it }, { throw expected })
        }.exceptionOrNull()
        assertSame(expected, failure)
    } }
}
