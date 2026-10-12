package dev.ikna.ui.session

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkEntity
import dev.ikna.domain.session.Level
import dev.ikna.domain.session.SessionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CardLoadingTest {
    @Test
    fun failedReadIsNotSuccessfulEmptyDataAndCanBeRetried() = runBlocking {
        var calls = 0
        val failure = IllegalStateException("disposable reader unavailable")
        suspend fun read(): List<String> {
            calls++
            if (calls == 1) throw failure
            return listOf("card")
        }
        val first = cardLoadResult { read() }
        assertTrue(first.isFailure)
        assertSame(failure, first.exceptionOrNull())
        assertNull(first.getOrNull())
        assertEquals(listOf("card"), cardLoadResult { read() }.getOrThrow())
        assertEquals(2, calls)
    }

    @Test
    fun genuinelyEmptyDataIsSuccessful() = runBlocking {
        val result = cardLoadResult { emptyList<String>() }
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrThrow())
    }

    @Test
    fun cancellationIsNeverReportedAsFailure() = runBlocking {
        val cancellation = CancellationException("screen closed")
        val thrown = assertFailsWith<CancellationException> {
            cardLoadResult<List<String>> { throw cancellation }
        }
        assertSame(cancellation, thrown)
    }

    @Test
    fun cancelledSuspendedLoadDoesNotPublishAnEmptyOrFailedResult() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<List<String>>()
        var published = false
        val job = launch {
            cardLoadResult {
                entered.complete(Unit)
                blocked.await()
            }
            published = true
        }
        entered.await()
        job.cancelAndJoin()
        assertFalse(published)
    }

    @Test
    fun readerSwallowingCancellationStillCannotPublishSuccess() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        var published = false
        val job = launch {
            cardLoadResult {
                entered.complete(Unit)
                try { blocked.await() } catch (_: CancellationException) { }
                emptyList<String>()
            }
            published = true
        }
        entered.await()
        job.cancelAndJoin()
        assertFalse(published)
    }

    @Test
    fun repeatedRetryCannotQueueAnotherLoadWhileOneIsPending() {
        val gate = CardLoadGate()
        assertTrue(gate.tryStart())
        repeat(20) { assertFalse(gate.tryStart()) }
        gate.finish()
        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
    }

    @Test
    fun failureAfterCompletionMustNotClaimCompletionAndRetryClearsFailure() {
        val completed = SessionUiState(loading = false, finished = true,
            answeredToday = 4, sessionDone = 4, sessionTotal = 4, undoVisible = true)
        val failed = completed.failedLoad()
        assertFalse(failed.loading)
        assertTrue(failed.loadFailed)
        assertFalse(failed.finished)
        assertFalse(failed.undoVisible)
        assertFalse(failed.canUndo)
        assertNull(failed.current)
        assertEquals(4, failed.answeredToday)
        val retry = failed.beginLoad()
        assertTrue(retry.loading)
        assertFalse(retry.loadFailed)
        assertFalse(retry.finished)
        assertEquals(4, retry.sessionDone)
    }

    @Test
    fun loadingAndFailureMakeStaleCardsUnavailableWithoutChangingCounts() {
        val chunk = ChunkEntity(
            id = "load-test", packId = "disposable", lang = "en", text = "test",
            contextSentence = "A test.", translation = "test", targetStart = 2, targetEnd = 6, freqRank = 1
        )
        val card = SessionCard(
            CardEntity(chunkId = chunk.id, level = 0, stability = 5.0, difficulty = 5.0,
                dueAt = 0L, lastReviewAt = null, introducedAt = 0L, reps = 1, isNew = false),
            chunk, Level.RECOGNITION, false
        )
        val ready = SessionUiState(loading = false, queue = listOf(card, card),
            answeredToday = 4, sessionDone = 2, sessionTotal = 4)
        assertSame(card, ready.current)
        assertSame(card, ready.nextCard)
        for (blocked in listOf(ready.copy(loading = true), ready.copy(loadFailed = true))) {
            assertNull(blocked.current)
            assertNull(blocked.nextCard)
            assertFalse(blocked.speakable)
            assertEquals(4, blocked.answeredToday)
            assertEquals(2, blocked.sessionDone)
            assertEquals(4, blocked.sessionTotal)
        }
        assertSame(card, ready.copy(loadFailed = false).current)
    }
}
