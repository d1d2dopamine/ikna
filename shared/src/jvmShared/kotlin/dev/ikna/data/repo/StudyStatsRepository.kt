package dev.ikna.data.repo

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import dev.ikna.data.db.IknaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A single read-only snapshot; no tracking, settings, schedule or history writes. */
class StudyStatsRepository(private val db: IknaDatabase, private val dayStartHour: Int = 4) {
    suspend fun export(days: Int?, format: StatsExportFormat, now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()): String = withContext(Dispatchers.IO) {
        require(days == null || days == 7 || days == 30)
        val from = days?.let { studyWindowStart(now, it, zone, dayStartHour) } ?: 0L
        val report = db.useReaderConnection { connection ->
            connection.deferredTransaction {
                val rows = db.reviewDao().studyAnswersBetween(from, now)
                val evidence = studyEvidence(rows, from, now)
                StatsExportReport(
                    days?.let { exportTime(from) }, exportTime(now), zone.id, dayStartHour,
                    evidence.targets, db.reviewDao().firstRecordedTargetCount(from, now),
                    evidence.answers, evidence.repeats, evidence.recalled, evidence.unclassified,
                    rows.map { StatsExportAnswer(exportTime(it.ts), it.chunkId, it.level, it.rating) }
                )
            }
        }
        report.render(format)
    }

    suspend fun load(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): StudyStats =
        withContext(Dispatchers.IO) {
            val caller = currentCoroutineContext()
            caller.ensureActive()
            val snapshot = db.useReaderConnection { connection ->
                connection.deferredTransaction {
                    val heatmapDays = 84
                    val heatmapStart = studyWindowStart(now, heatmapDays, zone, dayStartHour)
                    val monthStart = studyWindowStart(now, 30, zone, dayStartHour)
                    val rowsAll = db.reviewDao().studyAnswersBetween(0L, now)
                    val rows84 = rowsAll.filter { it.ts >= heatmapStart }
                    val rows = rows84.filter { it.ts >= monthStart }
                    val evidence = listOf(7, 30).associateWith { days ->
                        studyEvidence(rows, studyWindowStart(now, days, zone, dayStartHour), now)
                    }
                    val ids = evidence.values.flatMap { it.recovered + it.attention }.map { it.id }.distinct()
                    // Keep SQLite's bind-variable limit independent of retained history size.
                    val chunks = ids.chunked(400).flatMap { db.chunkDao().chunks(it) }.associateBy { it.id }
                    suspend fun period(days: Int): StudyPeriod {
                        val from = studyWindowStart(now, days, zone, dayStartHour)
                        val facts = evidence.getValue(days)
                        fun targets(items: List<TargetEvidence>) = items.map { item ->
                            val chunk = chunks[item.id]
                            StudyTarget(item.id, chunk?.text.orEmpty(), chunk?.translation.orEmpty(),
                                chunk?.lang.orEmpty(), item.misses, item.laterRecalls)
                        }
                        return StudyPeriod(days, from, now, facts.targets,
                            db.reviewDao().firstRecordedTargetCount(from, now), facts.answers,
                            facts.repeats, facts.recalled, facts.unclassified,
                            targets(facts.recovered), targets(facts.attention))
                    }

                    // Rich factual profile insights from existing local cards, packs, and 84d journal.
                    val localNow = Instant.ofEpochMilli(now).atZone(zone)
                    val todayStudyDate = if (localNow.hour < dayStartHour) localNow.toLocalDate().minusDays(1) else localNow.toLocalDate()
                    val firstHeatmapDate = todayStudyDate.minusDays((heatmapDays - 1).toLong())
                    val dailyCounts = IntArray(heatmapDays)
                    for (r in rows84) {
                        if (r.undoOf != null || r.rating !in 1..4) continue
                        val zdt = Instant.ofEpochMilli(r.ts).atZone(zone)
                        val d = if (zdt.hour < dayStartHour) zdt.toLocalDate().minusDays(1) else zdt.toLocalDate()
                        val idx = ChronoUnit.DAYS.between(firstHeatmapDate, d).toInt()
                        if (idx in 0 until heatmapDays) dailyCounts[idx]++
                    }
                    val activeDays84d = dailyCounts.count { it > 0 }
                    val activeDays30d = dailyCounts.drop(heatmapDays - 30).count { it > 0 }

                    val validMonth = rows.filter { it.undoOf == null && it.rating in 1..4 }
                    val totalMs30d = validMonth.sumOf { it.durationMs.coerceIn(0L, 120_000L) }
                    val studyMinutes30d = ((totalMs30d + 30_000L) / 60_000L).toInt()
                    val durationsSec = validMonth
                        .map { it.durationMs.coerceIn(0L, 120_000L) }
                        .filter { it > 0L }
                        .sorted()
                    val medianSecPerCard30d = if (durationsSec.isNotEmpty()) {
                        ((durationsSec[durationsSec.size / 2] + 500L) / 1000L).toInt().coerceAtLeast(1)
                    } else 0

                    val allCards = db.cardDao().all().filter { it.level == 0 }
                    var mature = 0
                    var young = 0
                    var learning = 0
                    var stabilitySum = 0.0
                    var stabilityCount = 0
                    val forecast = IntArray(7)
                    val tomorrowStart = todayStudyDate.plusDays(1).atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
                    val dayMs = 86_400_000L
                    for (c in allCards) {
                        if (!c.isNew && c.reps > 0) {
                            when {
                                c.stability >= 21.0 -> mature++
                                c.stability >= 3.0 -> young++
                                else -> learning++
                            }
                            if (c.stability > 0.0) {
                                stabilitySum += c.stability
                                stabilityCount++
                            }
                        }
                        if (!c.inAmnesty && !c.isNew) {
                            val bucket = when {
                                c.dueAt < tomorrowStart -> 0
                                else -> (((c.dueAt - tomorrowStart) / dayMs) + 1L).toInt()
                            }
                            if (bucket in 0..6) forecast[bucket]++
                        }
                    }
                    val avgStability = if (stabilityCount > 0) stabilitySum / stabilityCount else 0.0

                    val packs = db.chunkDao().packs()
                    val allDeckProgress = packs.mapNotNull { pack ->
                        val total = db.chunkDao().chunkCountFor(pack.id)
                        if (total <= 0) null
                        else {
                            val known = db.chunkDao().knownCountFor(pack.id, 21.0)
                            val studied = db.chunkDao().knownCountFor(pack.id, 0.001)
                            val label = pack.title?.trim()?.takeIf { it.isNotEmpty() } ?: pack.id
                            ProfileDeckProgress(
                                title = label,
                                known = known.coerceAtMost(total),
                                total = total,
                                studied = studied.coerceAtMost(total),
                                lang = pack.lang.trim().lowercase()
                            )
                        }
                    }.sortedWith(
                        compareByDescending<ProfileDeckProgress> { it.known }
                            .thenByDescending { it.studied }
                            .thenBy { it.title.lowercase() }
                    )
                    val deckProgress = allDeckProgress.take(4)

                    val languages = allDeckProgress
                        .filter { it.lang.isNotBlank() }
                        .groupBy { it.lang }
                        .map { (lang, list) ->
                            ProfileLanguageSummary(
                                lang = lang,
                                decks = list.size,
                                mature = list.sumOf { it.known },
                                studied = list.sumOf { it.studied }
                            )
                        }
                        .sortedWith(
                            compareByDescending<ProfileLanguageSummary> { it.mature }
                                .thenByDescending { it.studied }
                                .thenBy { it.lang }
                        )

                    val validAll = rowsAll
                        .filter { it.undoOf == null && it.rating in 1..4 }
                        .sortedBy { it.ts }
                    val memberSinceMs = validAll.firstOrNull()?.ts
                    val firstMatureAtMs = validAll.firstOrNull { it.stabilityAfter >= 21.0 }?.ts
                    val hundredthAnswerAtMs = validAll.getOrNull(99)?.ts

                    // Study rhythm buckets over last 84 days: Morning (5-11), Afternoon (12-17), Evening (18-22), Night (23-4)
                    val rhythm = IntArray(4)
                    for (r in rows84) {
                        if (r.undoOf != null || r.rating !in 1..4) continue
                        val hour = Instant.ofEpochMilli(r.ts).atZone(zone).hour
                        val bucket = when (hour) {
                            in 5..11 -> 0
                            in 12..17 -> 1
                            in 18..22 -> 2
                            else -> 3
                        }
                        rhythm[bucket]++
                    }

                    // Recently learned / practiced real phrases from the learner's own cards
                    val practicedCards = allCards
                        .filter { !it.isNew && it.reps > 0 }
                        .sortedWith(
                            compareByDescending<dev.ikna.data.db.CardEntity> { it.stability >= 21.0 }
                                .thenByDescending { it.stability >= 3.0 }
                                .thenByDescending { it.lastReviewAt ?: 0L }
                                .thenByDescending { it.stability }
                        )
                        .take(4)
                    val recentChunkIds = practicedCards.map { it.chunkId }.distinct()
                    val recentChunksMap = if (recentChunkIds.isEmpty()) {
                        emptyMap()
                    } else {
                        db.chunkDao().chunks(recentChunkIds).associateBy { it.id }
                    }
                    val recentLearned = practicedCards.mapNotNull { card ->
                        val ch = recentChunksMap[card.chunkId] ?: return@mapNotNull null
                        if (ch.text.isBlank()) return@mapNotNull null
                        ProfileLearnedPhrase(
                            text = ch.text.trim(),
                            translation = ch.translation.trim(),
                            lang = ch.lang.trim().lowercase(),
                            isMature = card.stability >= 21.0
                        )
                    }

                    val insights = ProfileInsights(
                        matureCards = mature,
                        youngCards = young,
                        learningCards = learning,
                        totalCards = allCards.size,
                        avgStabilityDays = avgStability,
                        studyMinutes30d = studyMinutes30d,
                        medianSecPerCard30d = medianSecPerCard30d,
                        activeDays30d = activeDays30d,
                        activeDays84d = activeDays84d,
                        dailyActivity84d = dailyCounts.toList(),
                        forecastNext7d = forecast.toList(),
                        topDecks = deckProgress,
                        memberSinceMs = memberSinceMs,
                        totalAnswersAllTime = validAll.size,
                        firstMatureAtMs = firstMatureAtMs,
                        hundredthAnswerAtMs = hundredthAnswerAtMs,
                        rhythmBuckets = rhythm.toList(),
                        languages = languages,
                        recentLearned = recentLearned
                    )

                    StudyStats(period(7), period(30), zone.id, dayStartHour, db.cardDao().nextDueAt(now), insights)
                }
            }
            caller.ensureActive()
            snapshot
        }
}
