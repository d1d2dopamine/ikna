package dev.ikna.data.export

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PreWipeBackupTest {
    @Test fun journalAndSettingsFinishBeforeWipe() = runBlocking {
        val calls = mutableListOf<String>()
        backupJournalThenWipe(
            { calls += "journal"; "reviews.jsonl" },
            { calls += "settings"; "settings.json" },
            { calls += "wipe" }
        )
        assertEquals(listOf("journal", "settings", "wipe"), calls)
    }

    @Test fun emptyHistoryStillRequiresSettingsBackup() = runBlocking {
        var wiped = false
        var settingsSaved = false
        backupJournalThenWipe({ null }, { settingsSaved = true; "settings.json" }, { wiped = true })
        assertTrue(settingsSaved)
        assertTrue(wiped)
    }

    @Test fun storageFailureAndMissingSettingsBlockWipeAndAllowRetry() = runBlocking {
        var wipes = 0
        for (stage in 0..2) {
            val failure = runCatching {
                backupJournalThenWipe(
                    { if (stage == 0) throw IOException("journal full"); null },
                    { if (stage == 1) throw IOException("settings full"); null },
                    { wipes++ }
                )
            }.exceptionOrNull()
            assertTrue(failure is PreWipeBackupException)
            assertEquals(0, wipes)
        }
        backupJournalThenWipe({ null }, { "settings.json" }, { wipes++ })
        assertEquals(1, wipes)
    }

    @Test fun cancellationIsNotReportedAsBackupFailureAndNeverWipes() = runBlocking {
        var wiped = false
        val failure = runCatching {
            backupJournalThenWipe({ throw CancellationException("cancelled") }, { "settings" }, { wiped = true })
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertFalse(wiped)
    }

    @Test fun wipeFailureRemainsDistinctFromBackupFailure() = runBlocking {
        val expected = IOException("wipe failed")
        val failure = runCatching {
            backupJournalThenWipe({ "journal" }, { "settings" }, { throw expected })
        }.exceptionOrNull()
        assertSame(expected, failure)
    }

    @Test fun nullDocumentAndNullStreamAreStorageFailures() {
        assertTrue(runCatching {
            writeExportDocument<String>({ null }, { ByteArrayOutputStream() }) { it.write("history") }
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            writeExportDocument({ "document" }, { null }) { it.write("history") }
        }.exceptionOrNull() is IOException)
    }

    @Test fun writeAndCloseFailuresPropagateAndSuccessfulContentIsComplete() {
        for (failOnClose in listOf(false, true)) {
            val stream = object : OutputStream() {
                override fun write(value: Int) { if (!failOnClose) throw IOException("full") }
                override fun close() { if (failOnClose) throw IOException("close failed") }
            }
            assertTrue(runCatching {
                writeExportDocument({ "document" }, { stream }) { it.write("history") }
            }.exceptionOrNull() is IOException)
        }
        val stream = ByteArrayOutputStream()
        writeExportDocument({ "document" }, { stream }) { it.write("history\nsettings") }
        assertEquals("history\nsettings", stream.toString("UTF-8"))
    }
}
