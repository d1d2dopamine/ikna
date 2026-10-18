package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.session.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Real disposable Room databases and technical identifiers, without study text. */
class ReviewDurationIntegrationTest {
    private class Fixture {
        val home = Files.createTempDirectory("ikna-review-duration-").toFile()
        val db = openIknaDatabase(home.resolve("profile.db"))
        val config = GovernorConfig(earnedNewPerReviews = 4)
        fun components() = ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db)
        fun learning() = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(), db.statsDao(),
            db.governorDao(), db.planDao(), db.browseDao(), components(), Scheduler(dayStartHour = config.dayStartHour), ChunkSelector(), config)
        fun restore() = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(), components(), Scheduler(dayStartHour = config.dayStartHour), config)
        suspend fun seed() {
            db.chunkDao().upsertPack(PackEntity("pack", 1, "en", 3, NOW))
            val ids = listOf("required", "hard", "fresh")
            db.chunkDao().insertChunksIgnore(ids.mapIndexed { i, id -> ChunkEntity(id, "pack", "en", "", "", "", 0, 0, i) })
            db.chunkDao().insertTokensIgnore(ids.map { ChunkTokenEntity(it, 0, it, it, "NOUN", true, true, 1.0) })
            db.chunkDao().upsertPackChunks(ids.mapIndexed { i, id -> PackChunkEntity("pack", id, i) })
            db.cardDao().upsertAll(ids.filter { it != "fresh" }.map { id ->
                CardEntity(id, 0, 3.0, 5.0, NOW + 86_400_000, NOW - 86_400_000, NOW - 2 * 86_400_000,
                    reps = 8, lapses = if (id == "hard") 4 else 0, isNew = false)
            })
        }
        suspend fun card(id: String = "required") = SessionBuilder(db.cardDao(), db.chunkDao(), config).materialize(listOf("$id:0")).single()
        fun close() { db.close(); home.deleteRecursively() }
    }
    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try { f.seed(); block(f) } finally { f.close() }
    }

    private suspend fun jsonl(f: Fixture) = f.db.reviewDao().all().joinToString("\n") {
        ReviewRecord.json.encodeToString(ReviewRecord.serializer(), ReviewRecord.of(it))
    }
    private data class State(val reviews: List<ReviewEntity>, val cards: List<CardEntity>,
        val components: List<ComponentEntity>, val stats: List<DailyStatEntity>, val plan: DailyPlanEntity?, val boundary: HistoryBoundaryEntity?)
    private suspend fun state(f: Fixture) = State(f.db.reviewDao().all(), f.db.cardDao().all(),
        f.db.componentDao().all(), f.db.statsDao().lastDays(Int.MAX_VALUE), f.db.planDao().plan(DAY), f.db.historyBoundaryDao().current())
    private suspend fun plan(f: Fixture) {
        f.db.planDao().upsert(DailyPlanEntity(DAY, "required:0", 1, 40, 1, 0, "NO_HEADROOM", 0, NOW - 2000))
        f.db.statsDao().upsert(DailyStatEntity(DAY, 0, 1, 0, 1.0, false))
    }
    private fun auth(device: String) = JournalAuthorization(device, if (device == "a") "b" else "a",
        "group", "epoch", false, "operation", "d".repeat(64))
    private suspend fun consent(a: Fixture, b: Fixture) {
        val ar = a.restore().profileTransfer; val br = b.restore().profileTransfer
        val av = ar.snapshot(); val bv = br.snapshot()
        ar.commit(bv, ar.preview(bv), operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = auth("a"))
        br.commit(av, br.preview(av), operationId = "operation", operationDigest = "d".repeat(64), journalAuthorization = auth("b"))
    }
    private suspend fun transfer(a: Fixture, b: Fixture): Int {
        val ar = a.restore().incrementalJournal; val br = b.restore().incrementalJournal
        var head = ar.localHead(auth("a"))
        while (head.hasUnindexed) head = ar.localHead(auth("a"))
        var cursor = br.checkpoint(auth("b"), head).cursor
        var added = 0
        while (cursor != head.highWater) {
            val inventory = requireNotNull(ar.inventory(auth("a"), cursor, head.highWater))
            val data = ar.payload(auth("a"), br.missing(auth("b"), inventory))
            val result = br.apply(auth("b"), data)
            added += result.imported; cursor = result.ack.cursor
        }
        return added
    }

    @Test fun backwardWallClockCannotPersistNegativeDuration() = runBlocking {
        fixture { source -> fixture { target -> fixture { imported ->
            val shownAt = NOW + 5_000L
            val answerAt = NOW
            val elapsed = answerAt - shownAt
            assertEquals(-5_000L, elapsed)
            source.learning().answer(source.card(), Rating.GOOD, elapsed, answerAt)
            val row = source.db.reviewDao().all().single()
            assertEquals("Recording must prevent negative elapsed duration", 0L, row.durationMs)
            assertEquals(0L, source.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(answerAt, row.ts)
            assertEquals(1, target.restore().mergeJournal(source.restore().snapshotJournal()).imported)
            assertEquals(1, imported.restore().restoreFromJsonl(jsonl(source)).imported)
            assertEquals(source.restore().snapshotJournal(), target.restore().snapshotJournal())
            assertEquals(source.restore().snapshotJournal(), imported.restore().snapshotJournal())
        } } }
    }

    @Test fun `recording clamps duration extremes without changing timestamp signals or scheduler`() = runBlocking {
        val cases = listOf(Long.MIN_VALUE to 0L, -1L to 0L, 0L to 0L, 1L to 1L,
            119_999L to 119_999L, 120_000L to 120_000L, 120_001L to 120_000L,
            300_000L to 120_000L, Long.MAX_VALUE to 120_000L)
        for ((raw, expected) in cases) fixture { f ->
            val card = f.card()
            val signals = ReviewSignals(latencyMs = 70_123L, swipeVelocityX = 2.5f, peeked = true,
                inputMethod = "keyboard", peekSemantics = "required_v2", timingDiscardReason = "timeout")
            val oracle = Scheduler(dayStartHour = f.config.dayStartHour).apply(card.card, Rating.GOOD, NOW).card
            f.learning().answer(card, Rating.GOOD, raw, NOW, signals)
            val row = f.db.reviewDao().all().single()
            assertEquals("raw=$raw", expected, row.durationMs)
            assertEquals(expected, f.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(oracle, f.db.cardDao().card("required", 0))
            assertEquals(NOW, row.ts); assertEquals(3, row.inputRating); assertEquals(3, row.rating)
            assertEquals(signals.latencyMs, row.latencyMs); assertEquals(signals.swipeVelocityX, row.swipeVelocityX)
            assertEquals(signals.peeked, row.peeked); assertEquals(signals.inputMethod, row.inputMethod)
            assertEquals(signals.peekSemantics, row.peekSemantics); assertEquals(signals.timingDiscardReason, row.timingDiscardReason)
            assertNull(row.purpose)
        }
    }

    @Test fun `ordinary undo and replay use the recorded bounded duration exactly once`() = runBlocking {
        for (sync in listOf(false, true)) fixture { f ->
            val repo = f.learning().also { if (sync) it.syncHistoryBoundary = { 0L } }
            repo.answer(f.card(), Rating.GOOD, 30L, NOW)
            val priorCard = f.db.cardDao().card("required", 0)
            repo.answer(f.card(), Rating.AGAIN, Long.MAX_VALUE, NOW + 1)
            assertEquals(120_030L, f.db.statsDao().day(DAY)!!.activeMs)
            assertEquals("required:0", repo.undoLast(NOW + 2))
            // Sync replay reconstructs introduction from the earliest retained observation.
            val expectedCard = if (sync) priorCard!!.copy(introducedAt = NOW) else priorCard
            assertEquals(expectedCard, f.db.cardDao().card("required", 0))
            assertEquals(30L, f.db.statsDao().day(DAY)!!.activeMs)
            val before = f.db.reviewDao().all()
            assertEquals(listOf(30L, 120_000L, 0L), before.map { it.durationMs })
            f.restore().replaySyncJournal(0)
            assertEquals(before, f.db.reviewDao().all())
            assertEquals(30L, f.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(1, f.db.statsDao().day(DAY)!!.reviewsDone)
        }
    }

    @Test fun `optional duration clamp and undo never earn goal or new card credit`() = runBlocking {
        fixture { f ->
            plan(f)
            val repo = f.learning()
            repo.answer(f.card(), Rating.GOOD, 100, NOW)
            val originalPlan = f.db.planDao().plan(DAY)
            val batch = requireNotNull(repo.startOptionalHardReview(now = NOW + 1))
            val cases = listOf(Long.MIN_VALUE to 0L, -1L to 0L, 0L to 0L, 1L to 1L,
                120_000L to 120_000L, 120_001L to 120_000L, Long.MAX_VALUE to 120_000L)
            cases.forEachIndexed { i, (raw, expected) ->
                val now = NOW + 2 + i * 2
                repo.answerOptionalHard(batch, batch.cards.single(), Rating.AGAIN, raw, now)
                assertEquals(expected, f.db.reviewDao().all().last().durationMs)
                assertEquals(ReviewPurpose.OPTIONAL_HARD, f.db.reviewDao().all().last().purpose)
                assertEquals(100 + expected, f.db.statsDao().day(DAY)!!.activeMs)
                assertEquals(2, repo.answeredToday(now)); assertEquals(1, repo.goalAnswersToday(now))
                assertNull(f.db.cardDao().card("fresh", 0)); assertEquals(originalPlan, f.db.planDao().plan(DAY))
                assertNotNull(repo.undoOptionalHard(batch, now + 1))
                assertEquals(100L, f.db.statsDao().day(DAY)!!.activeMs)
                assertEquals(1, repo.answeredToday(now + 1)); assertEquals(1, repo.goalAnswersToday(now + 1))
            }
            f.restore().replaySyncJournal(0)
            repo.answer(f.card(), Rating.GOOD, Long.MAX_VALUE, NOW + 100)
            assertEquals(120_100L, f.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(2, repo.goalAnswersToday(NOW + 100)); assertNull(f.db.cardDao().card("fresh", 0))
        }
    }

    @Test fun `bounded ordinary optional and undo events survive profile export restore and incremental sync`() = runBlocking {
        fixture { source -> fixture { peer -> fixture { imported ->
            consent(source, peer)
            plan(source)
            val repo = source.learning()
            repo.answer(source.card(), Rating.GOOD, 300_000, NOW)
            val batch = requireNotNull(repo.startOptionalHardReview(now = NOW + 1))
            repo.answerOptionalHard(batch, batch.cards.single(), Rating.AGAIN, Long.MAX_VALUE, NOW + 2)
            assertNotNull(repo.undoOptionalHard(batch, NOW + 3))
            repo.answerOptionalHard(batch, batch.cards.single(), Rating.GOOD, Long.MIN_VALUE, NOW + 4)
            repo.answer(source.card(), Rating.GOOD, 120_001, NOW + 5)
            val originalRows = source.db.reviewDao().all()
            assertEquals(listOf(120_000L, 120_000L, 0L, 0L, 120_000L), originalRows.map { it.durationMs })
            val profile = source.restore().profileTransfer.snapshot()
            assertEquals(profile, ProfileTransferCodec.decode(ProfileTransferCodec.encode(profile)))
            assertEquals(5, transfer(source, peer))
            source.restore().replaySyncJournal(0)
            assertEquals(originalRows, source.db.reviewDao().all())
            assertEquals(source.restore().snapshotJournal(), peer.restore().snapshotJournal())
            assertEquals(source.db.cardDao().all(), peer.db.cardDao().all())
            assertEquals(5, imported.restore().restoreFromJsonl(jsonl(source)).imported)
            assertEquals(source.restore().snapshotJournal(), imported.restore().snapshotJournal())
            for (f in listOf(source, peer, imported)) {
                assertEquals(240_000L, f.db.statsDao().day(DAY)!!.activeMs)
                assertEquals(3, f.db.statsDao().day(DAY)!!.reviewsDone)
                assertEquals(2, f.learning().goalAnswersToday(NOW + 10))
            }
            val before = state(peer)
            assertEquals(0, transfer(source, peer)); assertEquals(before, state(peer))
        } } }
    }

    @Test fun `historical long durations remain lossless through restore merge and replay`() = runBlocking {
        fixture { source -> fixture { target ->
            val initial = source.card().card
            val scheduled = Scheduler(dayStartHour = source.config.dayStartHour).apply(initial, Rating.GOOD, NOW)
            val old = ReviewRecord(chunkId = "required", ts = NOW, rating = 3, eventId = "historical-long",
                durationMs = 300_000L, elapsedDays = scheduled.elapsedDays,
                stabilityBefore = initial.stability, stabilityAfter = scheduled.card.stability,
                difficultyBefore = initial.difficulty, difficultyAfter = scheduled.card.difficulty,
                prevStability = initial.stability, prevDifficulty = initial.difficulty,
                prevLastReviewAt = initial.lastReviewAt, prevDueAt = initial.dueAt,
                prevIsNew = false, prevReps = initial.reps, prevLapses = initial.lapses)
            val encoded = ReviewRecord.json.encodeToString(ReviewRecord.serializer(), old)
            assertEquals(1, source.restore().restoreFromJsonl(encoded).imported)
            assertEquals(300_000L, source.db.reviewDao().all().single().durationMs)
            val retained = source.db.reviewDao().all().single()
            source.learning().answer(source.card(), Rating.GOOD, 300_000L, NOW + 1)
            assertEquals(retained, source.db.reviewDao().all().first())
            assertEquals(420_000L, source.db.statsDao().day(DAY)!!.activeMs)
            source.restore().replayFromLog()
            assertEquals(420_000L, source.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(2, target.restore().mergeJournal(source.restore().snapshotJournal()).imported)
            assertEquals(source.restore().snapshotJournal(), target.restore().snapshotJournal())
        } }
    }

    @Test fun `mixed negative import rejects before preparation or any database changes`() = runBlocking {
        fixture { f ->
            plan(f)
            f.learning().answer(f.card(), Rating.GOOD, 50L, NOW)
            f.db.historyBoundaryDao().put(HistoryBoundaryEntity(afterLocalId = 0, token = "retained-boundary"))
            val before = state(f)
            var preparations = 0; var completions = 0
            val restore = RestoreRepository(f.db, f.db.cardDao(), f.db.reviewDao(), f.db.statsDao(), f.db.planDao(),
                f.components(), Scheduler(), f.config,
                beforeExternalRestore = { preparations++; 0L }, afterExternalRestore = { completions++ })
            val valid = ReviewRecord(chunkId = "hard", ts = NOW + 1, rating = 3, eventId = "valid", durationMs = 300_000)
            for (negative in listOf(-1L, Long.MIN_VALUE)) {
                val rows = listOf(valid, valid.copy(eventId = "invalid", durationMs = negative))
                val text = rows.joinToString("\n") { ReviewRecord.json.encodeToString(ReviewRecord.serializer(), it) }
                try { restore.restoreFromJsonl(text); fail("Expected negative duration rejection") }
                catch (expected: IllegalArgumentException) { assertEquals("Invalid review duration", expected.message) }
                assertEquals(0, preparations); assertEquals(0, completions); assertEquals(before, state(f))
            }
        }
    }

    @Test fun `checked duration aggregate rolls back ordinary and optional answer atomically`() = runBlocking {
        for (optional in listOf(false, true)) fixture { f ->
            plan(f)
            val repo = f.learning()
            repo.answer(f.card(), Rating.GOOD, 100L, NOW)
            val batch = if (optional) requireNotNull(repo.startOptionalHardReview(now = NOW + 1)) else null
            val normalStat = f.db.statsDao().day(DAY)!!
            f.db.statsDao().upsert(normalStat.copy(activeMs = Long.MAX_VALUE - 99))
            val before = state(f)
            try {
                if (batch == null) repo.answer(f.card(), Rating.AGAIN, 100L, NOW + 2)
                else repo.answerOptionalHard(batch, batch.cards.single(), Rating.AGAIN, 100L, NOW + 2)
                fail("Expected checked aggregate overflow")
            } catch (_: ArithmeticException) { }
            assertEquals(before, state(f))
            f.db.statsDao().upsert(normalStat)
            if (batch == null) repo.answer(f.card(), Rating.AGAIN, 100L, NOW + 2)
            else repo.answerOptionalHard(batch, batch.cards.single(), Rating.AGAIN, 100L, NOW + 2)
            assertEquals(200L, f.db.statsDao().day(DAY)!!.activeMs)
            assertEquals(2, f.db.reviewDao().all().size)
        }
    }

    @Test fun `new recording never rewrites already stored invalid history`() = runBlocking {
        fixture { f ->
            val invalid = ReviewRecord(chunkId = "required", ts = NOW - 1, rating = 3,
                eventId = "retained-invalid", durationMs = -5_000L).toEntity()
            f.db.reviewDao().insert(invalid)
            val retained = f.db.reviewDao().all().single()
            f.learning().answer(f.card(), Rating.GOOD, -5_000L, NOW)
            assertEquals(retained, f.db.reviewDao().all().first())
            assertEquals(listOf(-5_000L, 0L), f.db.reviewDao().all().map { it.durationMs })
        }
    }

    companion object {
        private val NOW = LocalDateTime.of(2026, 10, 12, 12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        private const val DAY = "2026-10-12"
    }
}
