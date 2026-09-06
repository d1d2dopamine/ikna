package dev.ikna.data.repo
import dev.ikna.data.db.ReviewEntity
import dev.ikna.domain.fsrs.*
import java.security.MessageDigest

data class OptimizerInput(val samples: List<ReviewSample>, val fingerprint: String)
/** Timing and input-method exclusions belong to grading, NOT to FSRS fitting. */
fun optimizerInput(rows: List<ReviewEntity>, now: Long, suppressed: Set<String> = emptySet()): OptimizerInput {
    val undone = rows.mapNotNull { it.undoOf }.toSet()
    val answers = rows.asSequence().filter { it.undoOf == null && it.id !in undone &&
        it.rating in 1..4 && it.ts in 1L..now && it.chunkId !in suppressed }
        .sortedWith(compareBy<ReviewEntity> { it.ts }.thenBy { it.id })
        .distinctBy { Triple(it.chunkId, it.level, it.ts) }.toList().takeLast(FsrsOptimizer.MAX_ANSWERS)
    val digest = MessageDigest.getInstance("SHA-256")
    val samples = answers.map { row ->
        require(row.gradingVersion == null || row.gradingVersion == 1)
        row.fsrsParameters?.let(FsrsSnapshotCodec::decode)
        val grade = Rating.of(row.rating)
        val input = if (row.gradingVersion == 1) Rating.of(row.inputRating ?: 0) else grade
        require(row.gradingVersion != 1 || (input == Rating.GOOD && grade in listOf(Rating.HARD, Rating.EASY)))
        val key = row.chunkId + ":" + row.level; val model = row.fsrsParameters.orEmpty()
        digest.update(("${row.id}:${key.length}:$key:${row.ts}:${row.rating}:${input.value}:" +
            "${row.gradingVersion}:${model.length}:$model\n").toByteArray(Charsets.UTF_8))
        ReviewSample(key, row.ts, grade, input, row.gradingVersion)
    }
    return OptimizerInput(samples, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
}
