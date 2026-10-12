package dev.ikna.data.repo

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import dev.ikna.data.db.IknaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** A single read-only snapshot; no tracking, settings, schedule or history writes. */
class StudyStatsRepository(private val db: IknaDatabase, private val dayStartHour: Int = 4) {
    suspend fun load(now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): StudyStats =
        withContext(Dispatchers.IO) {
            val caller = currentCoroutineContext()
            caller.ensureActive()
            val snapshot = db.useReaderConnection { connection ->
                connection.deferredTransaction {
                    val monthStart = studyWindowStart(now, 30, zone, dayStartHour)
                    val rows = db.reviewDao().studyAnswersBetween(monthStart, now)
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
                    StudyStats(period(7), period(30), zone.id, dayStartHour)
                }
            }
            caller.ensureActive()
            snapshot
        }
}
