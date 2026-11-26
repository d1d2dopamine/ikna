package dev.ikna.data.repo

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.ComponentDao
import dev.ikna.data.db.ComponentEntity
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.inTransaction
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.governor.ComponentKey
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min
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
    private val reviewDao: ReviewDao,
    private val db: IknaDatabase? = null
) {

    suspend fun componentsFor(lemmas: List<String>): Map<ComponentKey, ComponentEntity> =
        lemmas.distinct()
            .chunked(TOKEN_QUERY_BATCH)
            .flatMap { componentDao.byLemmas(it) }
            .associateBy { ComponentKey(it.lemma, it.pos) }

    /**
     * Loads language-scoped components (plus legacy unscoped fallback rows)
     * for a batch of candidate chunks, isolating homographs across languages.
     */
    suspend fun componentsForChunks(
        chunks: List<ChunkEntity>,
        tokensByChunk: Map<String, List<ChunkTokenEntity>>
    ): Map<ComponentKey, ComponentEntity> {
        val keys = ArrayList<String>()
        for (chunk in chunks) {
            for (t in tokensByChunk[chunk.id].orEmpty()) {
                val scoped = ComponentKey.scopedLemma(chunk.lang, t.lemma)
                keys += scoped
                if (scoped != t.lemma) keys += t.lemma
            }
        }
        return componentsFor(keys)
    }

    suspend fun all(): Map<ComponentKey, ComponentEntity> =
        componentDao.all().associateBy { ComponentKey(it.lemma, it.pos) }

    suspend fun recordAnswer(chunkId: String, rating: Int, ts: Long) {
        val tokens = chunkDao.tokensFor(listOf(chunkId)).filter { it.weight > 0.0 }
        if (tokens.isEmpty()) return

        val lang = chunkDao.chunk(chunkId)?.lang.orEmpty()
        val scopedLemmas = tokens.map { ComponentKey.scopedLemma(lang, it.lemma) }.distinct()
        val existing = componentDao
            .byLemmas(scopedLemmas)
            .associateBy { ComponentKey(it.lemma, it.pos) }

        val success = if (rating >= 3) 1.0 else 0.0
        val updates = tokens.map { t ->
            val storedLemma = ComponentKey.scopedLemma(lang, t.lemma)
            val key = ComponentKey(storedLemma, t.pos)
            val prev = existing[key]
            val elapsedDays = prev?.lastSeenAt?.let { (ts - it).coerceAtLeast(0L).toDouble() / DAY_MS }
            val exposures = (prev?.exposures ?: 0.0) + t.weight
            val successes = (prev?.successes ?: 0.0) + t.weight * success
            ComponentEntity(
                lemma = storedLemma,
                pos = t.pos,
                exposures = exposures,
                successes = successes,
                stabilityEst = estimateStability(exposures, successes, prev?.stabilityEst, elapsedDays, success),
                firstSeenAt = prev?.firstSeenAt ?: ts,
                lastSeenAt = ts
            )
        }
        componentDao.upsertAll(updates)
    }

    /**
     * Spacing-aware lexical stability heuristic (Cepeda et al., 2006).
     *
     * Massed repetitions inside the same session (< 6 hours apart) yield only
     * a tiny nudge so ten exposures in one evening cannot inflate stability to
     * 350+ days. Spaced retrievals across days receive full growth proportional
     * to elapsed time relative to current stability.
     */
    internal fun estimateStability(
        exposures: Double,
        successes: Double,
        previous: Double?,
        elapsedDays: Double?,
        success: Double
    ): Double {
        val base = previous ?: 1.0
        return if (success > 0.0) {
            val rate = (successes / max(exposures, 0.001)).coerceIn(0.0, 1.0)
            val spacingFactor = when {
                previous == null || elapsedDays == null -> 1.0
                elapsedDays < SAME_SESSION_THRESHOLD_DAYS -> MASSED_GROWTH_SCALE
                else -> min(1.0, elapsedDays / max(base * 0.5, 1.0))
            }
            val multiplier = 1.0 + spacingFactor * (0.35 + rate * 0.45)
            (base * multiplier).coerceAtMost(365.0)
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
    var historyBoundary: (() -> Long?)? = null

    suspend fun rebuildFromReviews(orderedAnswers: List<dev.ikna.data.db.ReviewEntity>? = null, checkActive: () -> Unit = {}) {
        val caller = currentCoroutineContext()
        val check = { caller.ensureActive(); checkActive() }
        if (db != null) db.inTransaction { rebuildBody(orderedAnswers, check) }
        else rebuildBody(orderedAnswers, check)
    }

    private suspend fun rebuildBody(orderedAnswers: List<dev.ikna.data.db.ReviewEntity>?, checkActive: () -> Unit) {
        val caller = currentCoroutineContext()
        fun checkCancellation() {
            caller.ensureActive()
            checkActive() // Restore supplies the context captured outside Room.
        }
        checkCancellation()
        val configuredBoundary = if (orderedAnswers == null) historyBoundary?.invoke() else null
        val activeBoundary = if (orderedAnswers == null) db?.historyBoundaryDao()?.current()?.afterLocalId ?: configuredBoundary else null
        val cutoff = activeBoundary ?: 0L
        componentDao.clear()
        checkCancellation()
        val answers = orderedAnswers ?: reviewDao.allAnswers().filter { it.id > cutoff }.let { rows ->
            if (activeBoundary == null) rows else {
                val journal = reviewDao.all().filter { it.id > cutoff }
                val stable = dev.ikna.data.export.JournalIdentity.normalize(journal.map {
                    dev.ikna.data.export.ReviewRecord.of(it)
                })
                val identities = journal.zip(stable).associate { it.first.id to it.second.eventId!! }
                rows.sortedWith(compareBy<dev.ikna.data.db.ReviewEntity> { it.ts }.thenBy { identities[it.id] })
            }
        }
        if (answers.isEmpty()) return

        // Eager, not a Sequence, and that is load-bearing rather than a style
        // choice: Sequence.flatMap keeps its lambda for later, so it is not
        // inline, so a suspend call cannot appear inside it. List.flatMap is
        // inline and runs here and now, which keeps the suspend context. The
        // sequence version did not compile.
        val chunkIds = answers.map { it.chunkId }.distinct()
        val langByChunk = chunkIds
            .chunked(TOKEN_QUERY_BATCH)
            .flatMap { checkCancellation(); chunkDao.chunks(it) }
            .associate { it.id to it.lang }
        val tokens = chunkIds
            .chunked(TOKEN_QUERY_BATCH)
            .flatMap { checkCancellation(); chunkDao.tokensFor(it) }
            .filter { it.weight > 0.0 }
            .groupBy { it.chunkId }

        val acc = HashMap<ComponentKey, ComponentEntity>()
        for (r in answers) {
            checkCancellation()
            val success = if (r.outcomeRating >= 3) 1.0 else 0.0
            val lang = langByChunk[r.chunkId].orEmpty()
            for (t in tokens[r.chunkId].orEmpty()) {
                val storedLemma = ComponentKey.scopedLemma(lang, t.lemma)
                val key = ComponentKey(storedLemma, t.pos)
                val prev = acc[key]
                val elapsedDays = prev?.lastSeenAt?.let { (r.ts - it).coerceAtLeast(0L).toDouble() / DAY_MS }
                val exposures = (prev?.exposures ?: 0.0) + t.weight
                val successes = (prev?.successes ?: 0.0) + t.weight * success
                acc[key] = ComponentEntity(
                    lemma = storedLemma,
                    pos = t.pos,
                    exposures = exposures,
                    successes = successes,
                    stabilityEst =
                        estimateStability(exposures, successes, prev?.stabilityEst, elapsedDays, success),
                    firstSeenAt = prev?.firstSeenAt ?: r.ts,
                    lastSeenAt = r.ts
                )
            }
        }

        acc.values.chunked(UPSERT_BATCH).forEach {
            checkCancellation()
            componentDao.upsertAll(it)
        }
        checkCancellation()
    }

    /** Danger zone: wipes the derived word layer. Rebuildable from `reviews`. */
    suspend fun clearAll() = componentDao.clear()

    suspend fun knownWordCount(): Int = componentDao.knownCount(minStability = 7.0)

    private companion object {
        /** SQLite refuses an IN list longer than 999 bound arguments. */
        const val TOKEN_QUERY_BATCH = 400
        const val UPSERT_BATCH = 500
        /** Reviews separated by less than 6 hours are treated as massed practice. */
        const val SAME_SESSION_THRESHOLD_DAYS = 0.25
        /** Small cap on same-session stability gain to respect the spacing effect. */
        const val MASSED_GROWTH_SCALE = 0.05
    }
}
