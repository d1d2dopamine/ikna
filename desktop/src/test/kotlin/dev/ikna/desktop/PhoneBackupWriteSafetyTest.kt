package dev.ikna.desktop

import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.export.ReviewRecord
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class PhoneBackupWriteSafetyTest {
    private val review = ReviewEntity(
        chunkId = "fixture", level = 0, ts = 1, rating = 3, elapsedDays = 0.0,
        stabilityBefore = 0.0, stabilityAfter = 1.0, difficultyBefore = 0.0,
        difficultyAfter = 5.0, durationMs = 100, wasAmnesty = false
    )

    private fun fixture(test: suspend (File, File, File) -> Unit) = runBlocking {
        val home = Files.createTempDirectory("ikna-phone-write-").toFile()
        try {
            val names = IknaBundle.writeForPhone(home, listOf(review), "{}", false, 0)
            val history = File(home, names.first { it.endsWith(".jsonl") }).apply { writeText("old history") }
            val settings = File(home, names.first { it.endsWith(".json") }).apply { writeText("old settings") }
            test(home, history, settings)
        } finally { home.deleteRecursively() }
    }

    @Test fun midHistoryFailurePreservesPreviousFilesAndCleansTemp() = fixture { home, history, settings ->
        val expected = IOException("injected read failure")
        val reviews = object : AbstractList<ReviewEntity>() {
            override val size = 2
            override fun get(index: Int): ReviewEntity = if (index == 0) review else throw expected
        }
        val failure = runCatching { IknaBundle.writeForPhone(home, reviews, "{}", false, 0) }.exceptionOrNull()
        assertSame(expected, failure)
        assertEquals("old history", history.readText())
        assertEquals("old settings", settings.readText())
        assertEquals(2, home.listFiles()!!.size)
    }

    @Test fun cancellationDuringHistoryDoesNotPublishPartialFile() = fixture { home, history, settings ->
        val job = Job()
        val reviews = object : AbstractList<ReviewEntity>() {
            override val size = 2
            override fun get(index: Int): ReviewEntity {
                if (index == 1) job.cancel()
                return review
            }
        }
        val failure = runCatching {
            withContext(job) { IknaBundle.writeForPhone(home, reviews, "{}", false, 0) }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals("old history", history.readText())
        assertEquals("old settings", settings.readText())
        assertEquals(2, home.listFiles()!!.size)
    }

    @Test fun emptyHistoryDoesNotReplaceAnEarlierJournal() = fixture { home, history, settings ->
        val names = IknaBundle.writeForPhone(home, emptyList(), "{}", false, 0)
        assertEquals(listOf(settings.name), names)
        assertEquals("old history", history.readText())
        assertEquals("{}", settings.readText())
        assertEquals(2, home.listFiles()!!.size)
    }

    @Test fun secondFileFailureDoesNotUndoAlreadyPublishedCompleteHistory() = fixture { home, history, settings ->
        assertTrue(settings.delete())
        assertTrue(settings.mkdir())
        File(settings, "marker").writeText("keep")
        val failure = runCatching {
            IknaBundle.writeForPhone(home, listOf(review), "{}", false, 0)
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        val row = ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), history.readText().trim())
        assertEquals(review.chunkId, row.chunkId)
        assertEquals("keep", File(settings, "marker").readText())
        assertEquals(2, home.listFiles()!!.size)
    }
}
