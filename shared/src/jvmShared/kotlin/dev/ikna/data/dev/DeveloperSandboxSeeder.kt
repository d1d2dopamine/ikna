package dev.ikna.data.dev

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.DailyPlanEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.GovernorLogEntity
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.PackChunkEntity
import dev.ikna.data.db.PackEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.inTransaction
import dev.ikna.data.db.wipeAllData
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.CURRENT_SCHEDULER_VERSION
import dev.ikna.domain.fsrs.FsrsParams
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.fsrs.ComponentPrior
import dev.ikna.domain.time.DayBoundary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A compact description shown in Settings after a deterministic seed. */
data class DeveloperSandboxSummary(
    val scenario: DeveloperScenario,
    val reviewCount: Int,
    val cardCount: Int,
    val historyDays: Int
)

/**
 * Deterministic synthetic learner state for the isolated developer profile.
 *
 * This writes the real schema and real FSRS-shaped review/card rows. It never
 * runs against the real profile: containers only construct it for DEVELOPER.
 */
class DeveloperSandboxSeeder(
    private val db: IknaDatabase,
    private val settings: SettingsStore,
    private val components: ComponentRepository,
    private val config: GovernorConfig
) {
    private val chunks: ChunkDao get() = db.chunkDao()
    private val scheduler = Scheduler(
        FsrsParams(desiredRetention = config.desiredRetention),
        dayStartHour = config.dayStartHour
    )
    private val boundary = DayBoundary(config.dayStartHour)

    suspend fun seed(
        scenario: DeveloperScenario,
        now: Long = System.currentTimeMillis()
    ): DeveloperSandboxSummary {
        db.wipeAllData()
        settings.clearAll()
        settings.setOnboardingDone(true)
        settings.setReminder(false, 20, 0)
        settings.setAutoExport(false)
        settings.setDeveloperScenario(scenario.id)

        val ids = installSyntheticContent(now)
        when (scenario) {
            DeveloperScenario.EMPTY -> Unit
            DeveloperScenario.EARLY_HISTORY -> generateHistory(ids, days = 4, perDay = 12, now = now)
            DeveloperScenario.MATURE_HISTORY -> generateHistory(ids, days = 28, perDay = 18, now = now)
            DeveloperScenario.BROWSE_READY -> {
                generateHistory(ids, days = 28, perDay = 18, now = now)
                prepareBrowseReady(ids, now)
            }
            DeveloperScenario.RICH_STATISTICS -> generateHistory(
                ids, days = 60, perDay = 22, now = now, rich = true
            )
            DeveloperScenario.RETURN_AFTER_BREAK -> {
                generateHistory(ids, days = 35, perDay = 16, now = now - 21L * DAY_MS, rich = true)
                val overdue = db.cardDao().all().mapIndexed { index, card ->
                    if (index % 3 == 0) card.copy(inAmnesty = true, dueAt = now - 7L * DAY_MS)
                    else card.copy(dueAt = now - 3L * DAY_MS)
                }
                db.cardDao().upsertAll(overdue)
            }
        }
        components.rebuildFromReviews()
        settings.setSchedulerVersion(CURRENT_SCHEDULER_VERSION)
        return summary(scenario)
    }

    suspend fun summary(scenario: DeveloperScenario = DeveloperScenario.EMPTY): DeveloperSandboxSummary {
        val stats = db.statsDao().lastDays(365)
        return DeveloperSandboxSummary(
            scenario = scenario,
            reviewCount = db.reviewDao().total(),
            cardCount = db.cardDao().all().size,
            historyDays = stats.size
        )
    }

    private suspend fun installSyntheticContent(now: Long): List<String> = db.inTransaction {
        val packSpecs = listOf(
            "developer-language" to "Developer · language",
            "developer-subject" to "Developer · subject",
            "developer-reading" to "Developer · reading"
        )
        val allIds = ArrayList<String>(72)
        for ((packIndex, spec) in packSpecs.withIndex()) {
            val (packId, title) = spec
            val packIds = ArrayList<String>(24)
            val chunkRows = ArrayList<ChunkEntity>(24)
            val tokenRows = ArrayList<ChunkTokenEntity>(24)
            val membershipRows = ArrayList<PackChunkEntity>(24)
            repeat(24) { localIndex ->
                val number = packIndex * 24 + localIndex + 1
                val target = "devterm%03d".format(number)
                val sentence = "Synthetic context for $target in the developer sandbox."
                val start = sentence.indexOf(target)
                val id = "dev-target-%03d".format(number)
                allIds += id
                packIds += id
                chunkRows += ChunkEntity(
                    id = id,
                    packId = packId,
                    lang = "en",
                    text = target,
                    contextSentence = sentence,
                    translation = "Synthetic meaning %03d".format(number),
                    targetStart = start,
                    targetEnd = start + target.length,
                    freqRank = number
                )
                tokenRows += ChunkTokenEntity(
                    chunkId = id,
                    position = 0,
                    surface = target,
                    lemma = target,
                    pos = "NOUN",
                    isTarget = true,
                    isContent = true,
                    weight = 1.0
                )
                membershipRows += PackChunkEntity(
                    packId = packId,
                    chunkId = id,
                    freqRank = number
                )
            }
            chunks.upsertPack(
                PackEntity(
                    id = packId,
                    version = 1,
                    lang = "en",
                    chunkCount = packIds.size,
                    installedAt = now + packIndex,
                    title = title,
                    isActive = true
                )
            )
            chunks.upsertChunks(chunkRows)
            chunks.upsertTokens(tokenRows)
            chunks.upsertPackChunks(membershipRows)
        }
        allIds
    }

    private suspend fun generateHistory(
        ids: List<String>,
        days: Int,
        perDay: Int,
        now: Long,
        rich: Boolean = false
    ) {
        val reviews = ArrayList<ReviewEntity>(days * perDay)
        val cards = linkedMapOf<String, CardEntity>()
        val stats = linkedMapOf<String, DailyStatEntity>()
        val baseDay = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())

        for (offset in days downTo 1) {
            val date = baseDay.minusDays(offset.toLong())
            var correct = 0
            var active = 0L
            repeat(perDay) { index ->
                val chunkId = ids[((days - offset) * 11 + index * 5) % ids.size]
                val ts = date.atTime(12, (index * 7) % 55)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val before = cards[chunkId] ?: scheduler.introduce(
                    chunkId = chunkId,
                    level = 0,
                    componentPrior = ComponentPrior(0.0, 1, emptyList()),
                    now = ts
                )
                val rating = ratingFor(offset, index, rich)
                val duration = 1200L + ((offset * 149 + index * 311) % 6200)
                val result = scheduler.apply(before, rating, ts)
                cards[chunkId] = result.card
                if (rating.value >= Rating.GOOD.value) correct++
                active += duration
                reviews += ReviewEntity(
                    chunkId = chunkId,
                    level = 0,
                    ts = ts,
                    rating = rating.value,
                    elapsedDays = result.elapsedDays,
                    stabilityBefore = result.before.stability,
                    stabilityAfter = result.after.stability,
                    difficultyBefore = result.before.difficulty,
                    difficultyAfter = result.after.difficulty,
                    durationMs = duration,
                    wasAmnesty = false,
                    prevStability = before.stability,
                    prevDifficulty = before.difficulty,
                    prevDueAt = before.dueAt,
                    prevLastReviewAt = before.lastReviewAt,
                    prevReps = before.reps,
                    prevLapses = before.lapses,
                    prevIsNew = before.isNew,
                    prevInAmnesty = before.inAmnesty,
                    inputRating = rating.value,
                    presentationLength = 12 + index % 24,
                    inputMethod = if (index % 3 == 0) "keyboard" else "tap",
                    peekSemantics = "required",
                    latencyMs = duration.coerceAtMost(60_000L),
                    peeked = true
                )
            }
            val key = boundary.key(
                date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
            stats[key] = DailyStatEntity(
                day = key,
                reviewsDone = perDay,
                newIntroduced = minOf(perDay, 8),
                activeMs = active,
                accuracy = correct.toDouble() / perDay.coerceAtLeast(1),
                planCompleted = true,
                correctCount = correct
            )
        }
        db.inTransaction {
            reviews.chunked(250).forEach { db.reviewDao().insertAll(it) }
            db.cardDao().upsertAll(cards.values.toList())
            db.statsDao().upsertAll(stats.values.toList())
        }
    }

    private fun ratingFor(dayOffset: Int, index: Int, rich: Boolean): Rating {
        val key = dayOffset * 31 + index * 17
        if (rich && index < 3 && key % 3 != 0) return Rating.AGAIN
        return when {
            key % 19 == 0 -> Rating.AGAIN
            key % 11 == 0 -> Rating.HARD
            key % 13 == 0 -> Rating.EASY
            else -> Rating.GOOD
        }
    }

    private suspend fun prepareBrowseReady(ids: List<String>, now: Long) {
        val reviewIds = ids.takeLast(6)
        val browseCandidateIds = ids.takeLast(12).take(6)
        val day = boundary.key(now)
        val currentCards = db.cardDao().all().associateBy { it.chunkId }.toMutableMap()
        browseCandidateIds.forEach { chunkId ->
            currentCards[chunkId]?.let { card ->
                currentCards[chunkId] = card.copy(
                    isNew = false,
                    inAmnesty = false,
                    dueAt = now + 30L * DAY_MS
                )
            }
        }
        val rows = ArrayList<ReviewEntity>()
        for ((index, chunkId) in reviewIds.withIndex()) {
            val ts = now - (60_000L * (reviewIds.size - index))
            val before = currentCards[chunkId] ?: scheduler.introduce(
                chunkId, 0, ComponentPrior(0.0, 1, emptyList()), ts
            )
            val result = scheduler.apply(before, Rating.GOOD, ts)
            currentCards[chunkId] = result.card
            rows += ReviewEntity(
                chunkId = chunkId,
                level = 0,
                ts = ts,
                rating = Rating.GOOD.value,
                elapsedDays = result.elapsedDays,
                stabilityBefore = result.before.stability,
                stabilityAfter = result.after.stability,
                difficultyBefore = result.before.difficulty,
                difficultyAfter = result.after.difficulty,
                durationMs = 2200L + index * 100L,
                wasAmnesty = false,
                prevStability = before.stability,
                prevDifficulty = before.difficulty,
                prevDueAt = before.dueAt,
                prevLastReviewAt = before.lastReviewAt,
                prevReps = before.reps,
                prevLapses = before.lapses,
                prevIsNew = before.isNew,
                prevInAmnesty = before.inAmnesty,
                inputRating = Rating.GOOD.value,
                presentationLength = 18,
                inputMethod = "tap",
                peekSemantics = "required",
                latencyMs = 2200L,
                peeked = true
            )
        }
        db.inTransaction {
            db.reviewDao().insertAll(rows)
            db.cardDao().upsertAll(currentCards.values.toList())
            db.planDao().upsert(
                DailyPlanEntity(
                    day = day,
                    plannedIds = reviewIds.joinToString(",") { "$it:0" },
                    plannedTotal = reviewIds.size,
                    capacity = reviewIds.size,
                    allowedNew = 0,
                    amnestyQuota = 0,
                    reason = GovernorReason.OK.name,
                    extraRequested = 0,
                    createdAt = now - 10 * 60_000L
                )
            )
            db.governorDao().insert(
                GovernorLogEntity(
                    ts = now - 10 * 60_000L,
                    day = day,
                    dueToday = reviewIds.size,
                    forecastAvg3d = reviewIds.size.toDouble(),
                    backlog = 0,
                    accuracyRecent = 0.9,
                    daysSinceLastSession = 1,
                    reviewsDoneToday = reviewIds.size,
                    capacity = reviewIds.size,
                    headroom = 1.0,
                    allowedNew = 0,
                    reason = GovernorReason.OK.name
                )
            )
            db.statsDao().upsert(
                DailyStatEntity(
                    day = day,
                    reviewsDone = reviewIds.size,
                    newIntroduced = 0,
                    activeMs = rows.sumOf { it.durationMs },
                    accuracy = 1.0,
                    planCompleted = true,
                    correctCount = reviewIds.size
                )
            )
        }
        settings.clearBrowseCredits()
    }
}
