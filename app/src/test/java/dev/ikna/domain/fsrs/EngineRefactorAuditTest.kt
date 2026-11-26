package dev.ikna.domain.fsrs

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkContextEntity
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.ComponentEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.ComponentKey
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorSignalCollector
import dev.ikna.domain.session.ContextPolicy
import dev.ikna.domain.time.DayBoundary
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression & scientific-rigor unit tests for the Phase 1 & Phase 2 engine refactor.
 */
class EngineRefactorAuditTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val boundary = DayBoundary(4, zone)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 3, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun card(id: String, stability: Double = 5.0, reps: Int = 3, isNew: Boolean = false) = CardEntity(
        chunkId = id,
        level = 0,
        stability = stability,
        difficulty = 5.0,
        dueAt = at(10, 12),
        lastReviewAt = if (isNew) null else at(5, 12),
        introducedAt = at(1, 12),
        reps = reps,
        lapses = 0,
        inAmnesty = false,
        isNew = isNew
    )

    @Test
    fun `late-night 03_30 study with 1-day interval advances to following study day instead of 30 minutes later`() {
        val scheduler = Scheduler(FsrsParams(), dayStartHour = 4)
        val now = at(10, 3, 30) // 30 minutes before dayStartHour=4
        val result = scheduler.apply(card("c-night", stability = 2.0, reps = 2), Rating.AGAIN, now)

        val gapHours = (result.card.dueAt - now).toDouble() / 3_600_000.0
        assertTrue("1-day interval at 03:30 must not collapse to 30 min; got ${gapHours}h", gapHours >= 20.0)
        assertEquals(boundary.startOfDay(result.card.dueAt), result.card.dueAt)
    }

    @Test
    fun `FSRS-6 deterministic fuzzing is monotonic, reproducible, and spreads review cohorts`() {
        // Short intervals (<= 2.5d) are never fuzzed.
        assertEquals(1.0, Fsrs.fuzzIntervalDays(1.0, seed = 42L), 1e-12)
        assertEquals(2.5, Fsrs.fuzzIntervalDays(2.5, seed = 42L), 1e-12)

        // Determinism: identical seed yields identical fuzzed interval.
        val first = Fsrs.fuzzIntervalDays(30.0, seed = 123456L)
        val second = Fsrs.fuzzIntervalDays(30.0, seed = 123456L)
        assertEquals(first, second, 0.0)

        // Monotonicity: for any fixed seed, larger base interval never produces a shorter fuzzed interval.
        val grid = listOf(1.0, 2.0, 2.5, 2.6, 3.0, 5.0, 7.0, 7.5, 14.0, 20.0, 21.0, 60.0, 180.0)
        for (seed in listOf(-999L, 0L, 1L, 7L, 42L, 123456789L)) {
            var prev = 0.0
            for (ivl in grid) {
                val fuzzed = Fsrs.fuzzIntervalDays(ivl, seed)
                assertTrue("Monotonicity violated at ivl=$ivl seed=$seed: $fuzzed < $prev", fuzzed >= prev)
                prev = fuzzed
            }
        }

        // Anti-clumping: 30 cards scheduled at identical 30-day stability spread across multiple dates.
        val scheduler = Scheduler(FsrsParams(), dayStartHour = 4)
        val now = at(10, 14)
        val dueDates = (1..30).map { idx ->
            val c = card("cohort-$idx", stability = 20.0, reps = 5)
            boundary.key(scheduler.apply(c, Rating.GOOD, now).card.dueAt)
        }.toSet()
        assertTrue("Expected cohort to spread across >= 3 distinct dates, got $dueDates", dueDates.size >= 3)
    }

    @Test
    fun `FsrsOptimizer histories preserves whole card histories under MAX_ANSWERS cap`() {
        val samples = ArrayList<ReviewSample>()
        val start = 1_700_000_000_000L
        // 500 cards * 50 reviews = 25,000 reviews (> MAX_ANSWERS = 20,000).
        // Each card starts early and ends late, so naive chronological slicing would truncate every card.
        for (c in 0 until 500) {
            val key = "card-$c:0"
            for (r in 0 until 50) {
                samples += ReviewSample(
                    cardKey = key,
                    ts = start + r * 2L * DAY_MS + c * 1000L,
                    rating = Rating.GOOD
                )
            }
        }

        val kept = FsrsOptimizer.histories(samples)
        val totalKept = kept.sumOf { it.size }
        assertTrue("Total reviews must respect MAX_ANSWERS cap", totalKept <= FsrsOptimizer.MAX_ANSWERS)
        assertTrue("Most active cards should be retained", kept.size == FsrsOptimizer.MAX_ANSWERS / 50)
        // Every retained card must keep its complete 50-review history starting from review 0!
        for (history in kept) {
            assertEquals("Card history must not be truncated mid-life", 50, history.size)
            assertTrue("First review of each kept card must be its true initial review", history.first().ts < start + DAY_MS)
        }
    }

    @Test
    fun `FsrsOptimizer keeps unscored short-term weights w17..w19 pinned to defaults`() {
        val samples = ArrayList<ReviewSample>()
        val start = 1_700_000_000_000L
        // Build 120 cards with 8 spaced reviews each (840 scored reviews >= MIN_SCORED_ANSWERS).
        for (c in 0 until 120) {
            var t = start + c * 60_000L
            for (r in 0 until 8) {
                val rating = if (r == 3 && c % 4 == 0) Rating.AGAIN else Rating.GOOD
                samples += ReviewSample("opt-$c:0", t, rating)
                t += (r + 2) * DAY_MS
            }
        }
        val res = FsrsOptimizer.optimise(samples)
        res.params?.let { fitted ->
            for (idx in 17..19) {
                assertEquals(
                    "Unscored short-term weight w[$idx] must remain at published default",
                    FsrsParams.DEFAULT_W[idx],
                    fitted.w[idx],
                    0.0
                )
            }
        }
    }

    @Test
    fun `ContextPolicy keeps acquisition context fixed until mature and rotates deterministically afterward`() {
        val base = ChunkEntity("ch1", "p1", "en", "run", "I run every morning.", "Я бегаю каждое утро.", 2, 5, 10)
        val contexts = listOf(
            ChunkContextEntity("p1", "ch1", "ctx-1", "m1", "s1", "I run every morning.", "Я бегаю каждое утро.", 2, 5, 10, null, "[]"),
            ChunkContextEntity("p1", "ch1", "ctx-2", "m1", "s2", "She can run fast.", "Она умеет быстро бегать.", 8, 11, 20, null, "[]"),
            ChunkContextEntity("p1", "ch1", "ctx-3", "m1", "s3", "They run along the river.", "Они бегут вдоль реки.", 5, 8, 30, null, "[]")
        )

        val young = card("ch1", stability = 5.0, reps = 2, isNew = false)
        assertEquals(base.contextSentence, ContextPolicy.resolveChunk(base, young, contexts).contextSentence)

        val fresh = card("ch1", stability = 30.0, reps = 1, isNew = true)
        assertEquals(base.contextSentence, ContextPolicy.resolveChunk(base, fresh, contexts).contextSentence)

        val matureRep0 = card("ch1", stability = 25.0, reps = 0, isNew = false)
        val matureRep1 = card("ch1", stability = 25.0, reps = 1, isNew = false)
        val matureRep2 = card("ch1", stability = 25.0, reps = 2, isNew = false)
        val s0 = ContextPolicy.resolveChunk(base, matureRep0, contexts).contextSentence
        val s1 = ContextPolicy.resolveChunk(base, matureRep1, contexts).contextSentence
        val s2 = ContextPolicy.resolveChunk(base, matureRep2, contexts).contextSentence
        assertEquals(setOf("I run every morning.", "She can run fast.", "They run along the river."), setOf(s0, s1, s2))
    }

    @Test
    fun `ChunkSelector isolates multilingual homographs like English gift vs German gift`() {
        val now = at(10, 12)
        val enChunk = ChunkEntity("en-1", "p-en", "en", "gift", "A nice gift", "Хороший подарок", 7, 11, 100)
        val deChunk = ChunkEntity("de-1", "p-de", "de", "Gift", "Das Gift wirkt", "Яд действует", 4, 8, 100)
        val tokens = mapOf(
            "en-1" to listOf(ChunkTokenEntity("en-1", 0, "gift", "gift", "NOUN", true, true, 1.0)),
            "de-1" to listOf(ChunkTokenEntity("de-1", 0, "Gift", "gift", "NOUN", true, true, 1.0))
        )
        // Learner knows English "gift" very well, has never seen German "Gift".
        val components = mapOf(
            ComponentKey.forLang("en", "gift", "NOUN") to ComponentEntity(
                lemma = ComponentKey.scopedLemma("en", "gift"),
                pos = "NOUN",
                exposures = 10.0,
                successes = 10.0,
                stabilityEst = 60.0,
                firstSeenAt = now - 30 * DAY_MS,
                lastSeenAt = now - DAY_MS
            )
        )
        val scored = ChunkSelector().select(listOf(enChunk, deChunk), tokens, components, now, 2)
        val enResult = scored.first { it.chunk.id == "en-1" }
        val deResult = scored.first { it.chunk.id == "de-1" }
        assertEquals(1.0, enResult.prior.knownRatio, 1e-9)
        assertEquals(0, enResult.prior.unknownContentTokens)
        assertEquals(0.0, deResult.prior.knownRatio, 1e-9)
        assertEquals(1, deResult.prior.unknownContentTokens)
    }

    @Test
    fun `overheating excludes the candidate day from its own accuracy and volume baseline`() {
        // 3 calm baseline days (20 reviews, 95% accuracy) + 1 overheated day yesterday (35 reviews, 81% accuracy).
        // If yesterday were included in its own average, usual = (0.95*3 + 0.81)/4 = 0.915 and drop = 0.105 < 0.12 (missed!).
        // Excluding yesterday gives usual = 0.95 and drop = 0.14 >= 0.12 -> detected!
        val days = listOf(
            DailyStatEntity("2026-03-09", reviewsDone = 35, newIntroduced = 2, activeMs = 60_000, accuracy = 0.81, planCompleted = true),
            DailyStatEntity("2026-03-08", reviewsDone = 20, newIntroduced = 2, activeMs = 40_000, accuracy = 0.95, planCompleted = true),
            DailyStatEntity("2026-03-07", reviewsDone = 20, newIntroduced = 2, activeMs = 40_000, accuracy = 0.95, planCompleted = true),
            DailyStatEntity("2026-03-06", reviewsDone = 20, newIntroduced = 2, activeMs = 40_000, accuracy = 0.95, planCompleted = true)
        )
        val config = GovernorConfig()
        assertTrue(
            GovernorSignalCollector.isOverheated(
                recentActiveDays = days,
                todayKey = "2026-03-10",
                overheatRatio = config.overheatRatio,
                overheatAccuracyDrop = config.overheatAccuracyDrop
            )
        )
        // Normal accurate heavy day is still NOT flagged as overheated.
        val healthyHeavy = listOf(days.first().copy(accuracy = 0.94)) + days.drop(1)
        assertFalse(
            GovernorSignalCollector.isOverheated(
                recentActiveDays = healthyHeavy,
                todayKey = "2026-03-10",
                overheatRatio = config.overheatRatio,
                overheatAccuracyDrop = config.overheatAccuracyDrop
            )
        )
    }

    @Test
    fun `interleaveNewAndReviews starts with warm-up reviews and spreads new items across the session`() {
        val newCards = (1..4).map { card("new-$it", isNew = true, reps = 0) }
        val reviews = (1..10).map { card("rev-$it", isNew = false, reps = 5) }

        val builder = dev.ikna.domain.session.SessionBuilder(
            cardDao = dummyProxy(),
            chunkDao = dummyProxy(),
            config = GovernorConfig()
        )
        val ordered = builder.interleaveNewAndReviews(newCards, reviews)
        assertEquals(14, ordered.size)
        assertEquals((newCards + reviews).map { it.key }.toSet(), ordered.map { it.key }.toSet())
        // First two cards are familiar warm-up reviews, not new cards.
        assertFalse(ordered[0].isNew)
        assertFalse(ordered[1].isNew)
        // New cards must not all be clustered consecutively.
        val newIndices = ordered.mapIndexedNotNull { idx, c -> if (c.isNew) idx else null }
        assertTrue("New cards should be spread across the session, got indices $newIndices", newIndices.last() - newIndices.first() > newCards.size)
        assertNotEquals(listOf(0, 1, 2, 3), newIndices)
    }

    private inline fun <reified T> dummyProxy(): T = java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, _, _ -> throw UnsupportedOperationException("Not called in pure ordering test") } as T
}
