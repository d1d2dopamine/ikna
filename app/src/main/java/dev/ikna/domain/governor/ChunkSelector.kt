package dev.ikna.domain.governor

import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.ComponentEntity
import dev.ikna.domain.fsrs.ComponentPrior
import kotlin.math.max

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
    private val weakRetrievabilityThreshold: Double = 0.8
) {

    fun select(
        candidates: List<ChunkEntity>,
        tokensByChunk: Map<String, List<ChunkTokenEntity>>,
        components: Map<ComponentKey, ComponentEntity>,
        now: Long,
        count: Int
    ): List<ScoredChunk> {
        if (count <= 0) return emptyList()
        val maxRank = max(1, candidates.maxOfOrNull { it.freqRank } ?: 1)

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
            // Frequency still matters: common phrases first.
            val frequency = 1.0 - (chunk.freqRank.toDouble() / maxRank)

            val score = novelty * 1.0 + repair + frequency * 0.5
            ScoredChunk(chunk, prior, score)
        }
            .sortedByDescending { it.score }
            .take(count)
    }

    private fun retrievability(elapsedDays: Double, stability: Double): Double =
        dev.ikna.domain.fsrs.Fsrs.retrievability(elapsedDays, stability)
}

data class ComponentKey(val lemma: String, val pos: String)
