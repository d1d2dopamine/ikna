package dev.ikna.data.repo

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ComponentDao
import dev.ikna.data.db.ComponentEntity
import dev.ikna.data.db.ReviewDao
import dev.ikna.domain.governor.ComponentKey
import kotlin.math.max
import kotlin.math.pow

/**
 * The component (word) layer.
 *
 * Information flows one way only: an answer updates card state and component
 * state independently, and component state influences card state exactly once,
 * as a prior at introduction time. Never after. Two-way updates diverge and
 * become impossible to debug.
 *
 * This table is derived. It can be dropped and rebuilt from `reviews` at any
 * time, which is what makes it safe to change the model later.
 */
class ComponentRepository(
    private val componentDao: ComponentDao,
    private val chunkDao: ChunkDao,
    private val reviewDao: ReviewDao
) {

    suspend fun componentsFor(lemmas: List<String>): Map<ComponentKey, ComponentEntity> =
        componentDao.byLemmas(lemmas.distinct()).associateBy { ComponentKey(it.lemma, it.pos) }

    suspend fun all(): Map<ComponentKey, ComponentEntity> =
        componentDao.all().associateBy { ComponentKey(it.lemma, it.pos) }

    suspend fun recordAnswer(chunkId: String, rating: Int, ts: Long) {
        val tokens = chunkDao.tokensFor(listOf(chunkId)).filter { it.weight > 0.0 }
        if (tokens.isEmpty()) return

        val existing = componentDao
            .byLemmas(tokens.map { it.lemma })
            .associateBy { ComponentKey(it.lemma, it.pos) }

        val success = if (rating >= 3) 1.0 else 0.0
        val updates = tokens.map { t ->
            val key = ComponentKey(t.lemma, t.pos)
            val prev = existing[key]
            val exposures = (prev?.exposures ?: 0.0) + t.weight
            val successes = (prev?.successes ?: 0.0) + t.weight * success
            ComponentEntity(
                lemma = t.lemma,
                pos = t.pos,
                exposures = exposures,
                successes = successes,
                stabilityEst = estimateStability(exposures, successes, prev?.stabilityEst, success),
                firstSeenAt = prev?.firstSeenAt ?: ts,
                lastSeenAt = ts
            )
        }
        componentDao.upsertAll(updates)
    }

    /**
     * Deliberately crude: an exponentially growing half-life on success, a hard
     * cut on failure. Good enough to seed a prior and to rank candidate chunks,
     * which is all this layer is allowed to influence.
     */
    private fun estimateStability(
        exposures: Double,
        successes: Double,
        previous: Double?,
        success: Double
    ): Double {
        val base = previous ?: 1.0
        return if (success > 0.0) {
            val rate = (successes / max(exposures, 0.001)).coerceIn(0.0, 1.0)
            (base * (1.35 + rate * 0.45)).coerceAtMost(365.0)
        } else {
            max(1.0, base * 0.45)
        }
    }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + (19.0 / 81.0) * elapsedDays / max(stability, 0.1)).pow(-0.5)

    /**
     * Replays the whole append-only review log to rebuild this table.
     *
     * Two things changed here, both of which made the rebuilt table disagree
     * with the one it replaced:
     *
     *  - it read `all()`, the raw log. That includes retractions, which carry
     *    `rating = 0` and were therefore counted as failures — undoing an answer
     *    damaged the word it contained instead of reverting it — and it includes
     *    the retracted answers themselves. `allAnswers()` is the log with both
     *    sides of every undo removed, which is what the live path counts;
     *  - it ran two queries per answer. On a four-month log that is tens of
     *    thousands of round trips through SQLite for a table that fits in
     *    memory, and it runs at the end of every restore.
     */
    suspend fun rebuildFromReviews() {
        componentDao.clear()
        val answers = reviewDao.allAnswers()
        if (answers.isEmpty()) return

        // Eager, not a Sequence, and that is load-bearing rather than a style
        // choice: Sequence.flatMap keeps its lambda for later, so it is not
        // inline, so a suspend call cannot appear inside it. List.flatMap is
        // inline and runs here and now, which keeps the suspend context. The
        // sequence version did not compile.
        val tokens = answers.map { it.chunkId }
            .distinct()
            .chunked(TOKEN_QUERY_BATCH)
            .flatMap { chunkDao.tokensFor(it) }
            .filter { it.weight > 0.0 }
            .groupBy { it.chunkId }

        val acc = HashMap<ComponentKey, ComponentEntity>()
        for (r in answers) {
            val success = if (r.rating >= 3) 1.0 else 0.0
            for (t in tokens[r.chunkId].orEmpty()) {
                val key = ComponentKey(t.lemma, t.pos)
                val prev = acc[key]
                val exposures = (prev?.exposures ?: 0.0) + t.weight
                val successes = (prev?.successes ?: 0.0) + t.weight * success
                acc[key] = ComponentEntity(
                    lemma = t.lemma,
                    pos = t.pos,
                    exposures = exposures,
                    successes = successes,
                    stabilityEst =
                        estimateStability(exposures, successes, prev?.stabilityEst, success),
                    firstSeenAt = prev?.firstSeenAt ?: r.ts,
                    lastSeenAt = r.ts
                )
            }
        }

        acc.values.chunked(UPSERT_BATCH).forEach { componentDao.upsertAll(it) }
    }

    /** Danger zone: wipes the derived word layer. Rebuildable from `reviews`. */
    suspend fun clearAll() = componentDao.clear()

    suspend fun knownWordCount(): Int = componentDao.knownCount(minStability = 7.0)

    private companion object {
        /** SQLite refuses an IN list longer than 999 bound arguments. */
        const val TOKEN_QUERY_BATCH = 400
        const val UPSERT_BATCH = 500
    }
}
