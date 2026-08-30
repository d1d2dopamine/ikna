package dev.ikna.domain.governor

import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.ComponentEntity
import dev.ikna.domain.fsrs.ComponentPrior
import kotlin.math.max
import kotlin.math.pow

data class ScoredChunk(
    val chunk: ChunkEntity,
    val prior: ComponentPrior,
    val score: Double
)

/**
 * Decides *which* chunks to introduce, once the governor has said how many.
 *
 * This is where the component layer pays for itself. A well chosen chunk can
 * simultaneously teach one new word and repair three decaying ones, which turns
 * part of "new material" into "review" and removes the usual competition
 * between them.
 */
class ChunkSelector(
    private val knownStabilityThreshold: Double = 7.0,
    private val weakRetrievabilityThreshold: Double = 0.8,
    /**
     * Rank at which a chunk's frequency bonus has fallen to half.
     *
     * The bonus used to be `1 - rank / maxRank`, where `maxRank` was the largest
     * rank in the candidate batch -- and the batch is just whatever the frequency
     * query returned that day. So the same chunk scored differently on different
     * days for reasons that had nothing to do with it: batched with rare words
     * its bonus was near 1, batched with common ones it collapsed towards 0. The
     * component layer, which is the entire reason this selector exists, was being
     * outvoted by an accident of pagination.
     *
     * An absolute scale instead. Rank 0 scores 1, rank 2000 scores 0.5, and
     * nothing depends on what else was in the batch. The number is a soft
     * preference for common language, not a cutoff.
     */
    private val frequencyHalfRank: Double = 2000.0
) {

    fun select(
        candidates: List<ChunkEntity>,
        tokensByChunk: Map<String, List<ChunkTokenEntity>>,
        components: Map<ComponentKey, ComponentEntity>,
        now: Long,
        count: Int
    ): List<ScoredChunk> {
        if (count <= 0) return emptyList()

        return candidates.mapNotNull { chunk ->
            val tokens = tokensByChunk[chunk.id]?.filter { it.isContent } ?: return@mapNotNull null
            if (tokens.isEmpty()) return@mapNotNull null

            var known = 0
            var unknown = 0
            val weak = ArrayList<String>()

            for (t in tokens) {
                val c = components[ComponentKey(t.lemma, t.pos)]
                if (c == null) {
                    unknown++
                    continue
                }
                val ageDays = (now - c.lastSeenAt).toDouble() / 86_400_000.0
                val r = retrievability(ageDays, c.stabilityEst)
                if (c.stabilityEst >= knownStabilityThreshold) known++ else unknown++
                if (r < weakRetrievabilityThreshold) weak += t.lemma
            }

            val knownRatio = known.toDouble() / tokens.size
            val prior = ComponentPrior(knownRatio, unknown, weak)

            // i+1: exactly one unknown content word is the sweet spot.
            val novelty = when (unknown) {
                0 -> 0.35   // pure consolidation, still useful but not new learning
                1 -> 1.0
                2 -> 0.55
                else -> 0.15
            }
            // Repairing decaying components is free value.
            val repair = (weak.size.coerceAtMost(4)) * 0.18
            // Frequency still matters: common phrases first. Measured against an
            // absolute scale, so a chunk's score says something about the chunk.
            val frequency = frequencyHalfRank / (frequencyHalfRank + chunk.freqRank)

            val score = novelty * 1.0 + repair + frequency * 0.5
            ScoredChunk(chunk, prior, score)
        }
            .sortedByDescending { it.score }
            .take(count)
    }

    /**
     * The component layer is a small ranking heuristic, not an FSRS card.
     * Keep its original curve when the item scheduler moves to FSRS-6: changing
     * it here would silently change which chunks the governor introduces, and
     * would mix a scheduler migration with a content-selection experiment.
     */
    private fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + (19.0 / 81.0) * elapsedDays / max(stability, 0.1)).pow(-0.5)
}

data class ComponentKey(val lemma: String, val pos: String)
