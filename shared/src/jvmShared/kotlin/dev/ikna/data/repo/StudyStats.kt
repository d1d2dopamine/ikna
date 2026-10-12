package dev.ikna.data.repo

import dev.ikna.data.db.ReviewEntity
import java.time.Instant
import java.time.ZoneId

/** Facts about the retained local journal, never a language-proficiency score. */
data class StudyStats(
    val week: StudyPeriod,
    val month: StudyPeriod,
    val zone: String,
    val dayStartHour: Int
)

data class StudyPeriod(
    val days: Int,
    val from: Long,
    val until: Long,
    val targets: Int,
    val firstRecordedTargets: Int,
    val answers: Int,
    val repeatAnswers: Int,
    val recalledRepeatAnswers: Int,
    val unclassifiedAnswers: Int,
    val recovered: List<StudyTarget>,
    val attention: List<StudyTarget>
)

data class StudyTarget(
    val id: String,
    val text: String,
    val translation: String,
    val language: String,
    val misses: Int,
    val laterRecalls: Int
)

internal data class TargetEvidence(val id: String, val misses: Int, val laterRecalls: Int, val lastAt: Long)
internal data class StudyEvidence(
    val targets: Int, val answers: Int, val repeats: Int, val recalled: Int,
    val unclassified: Int, val recovered: List<TargetEvidence>, val attention: List<TargetEvidence>
)

/** Calendar study days in the current device zone; DST does not turn a day into 24 fixed hours. */
internal fun studyWindowStart(now: Long, days: Int, zone: ZoneId, dayStartHour: Int): Long {
    require(days > 0)
    require(dayStartHour in 0..23)
    val local = Instant.ofEpochMilli(now).atZone(zone)
    val date = if (local.hour < dayStartHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
    return date.minusDays(days.toLong() - 1).atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
}

/** Input is the DAO's non-retracted journal; ids, not spellings, define a learning target. */
internal fun studyEvidence(rows: List<ReviewEntity>, from: Long, until: Long): StudyEvidence {
    val answers = rows.filter { it.ts in from..until && it.undoOf == null && it.rating in 1..4 }
    val repeats = answers.filter { it.prevIsNew == false }
    val recovered = mutableListOf<TargetEvidence>()
    val attention = mutableListOf<TargetEvidence>()
    // A first-contact answer or unknown old snapshot is not evidence of recall.
    answers.groupBy { it.chunkId }.forEach { (id, history) ->
        val sorted = history.sortedWith(compareBy<ReviewEntity> { it.ts }.thenBy { it.id })
        // Retired question levels remain in history. Never manufacture a sequence
        // by combining answers to different question types for one target.
        val latestLevel = sorted.last().level
        val ordered = sorted.filter { it.level == latestLevel }
        val lastMiss = ordered.indexOfLast { it.rating == 1 }
        val misses = ordered.count { it.rating == 1 && it.prevIsNew == false }
        // Unknown/first-contact successes do not supply recall evidence, but
        // any recorded miss interrupts the claimed recovery sequence.
        val later = if (lastMiss >= 0) ordered.drop(lastMiss + 1)
            .count { it.rating >= 2 && it.prevIsNew == false } else 0
        val evidence = TargetEvidence(id, misses, later, ordered.last().ts)
        if (lastMiss >= 0 && ordered[lastMiss].prevIsNew == false && later >= 2) recovered += evidence
        if (misses >= 2 && ordered.last().rating == 1 && ordered.last().prevIsNew == false) attention += evidence
    }
    val ordering = compareByDescending<TargetEvidence> { it.lastAt }.thenBy { it.id }
    return StudyEvidence(
        targets = answers.map { it.chunkId }.distinct().size,
        answers = answers.size,
        repeats = repeats.size,
        recalled = repeats.count { it.rating >= 2 },
        unclassified = answers.count { it.prevIsNew == null },
        recovered = recovered.sortedWith(ordering),
        attention = attention.sortedWith(ordering)
    )
}
