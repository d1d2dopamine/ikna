package dev.ikna.domain.session

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkContextEntity
import dev.ikna.data.db.ChunkEntity

/**
 * Conservative contextual-diversity selector for Catalogue v2 targets.
 *
 * Evidence from L1/L2 word-learning meta-analyses (Chen et al., 2026;
 * Studies in Second Language Acquisition, 2026) and transfer reviews
 * (Pan & Rickard, 2018) supports keeping the initial acquisition context
 * stable while a memory is still fragile, then rotating across natural
 * contexts once the item has consolidated so retrieval transfers beyond
 * a single memorized carrier sentence.
 *
 * Selection is pure and deterministic from `card.reps` so live sessions
 * never flicker mid-card and replayed state remains reproducible.
 */
object ContextPolicy {
    /** Stability threshold (days) required before rotating beyond the primary context. */
    const val DIVERSITY_MIN_STABILITY_DAYS = 21.0

    fun resolveChunk(
        chunk: ChunkEntity,
        card: CardEntity,
        contexts: List<ChunkContextEntity>
    ): ChunkEntity {
        if (card.isNew || card.stability < DIVERSITY_MIN_STABILITY_DAYS) return chunk
        val distinct = contexts
            .filter { it.contextSentence.isNotBlank() }
            .distinctBy { it.contextSentence }
        if (distinct.size <= 1) return chunk
        val chosen = distinct[Math.floorMod(card.reps, distinct.size)]
        return chunk.copy(
            contextSentence = chosen.contextSentence,
            translation = chosen.translation.ifBlank { chunk.translation },
            targetStart = chosen.targetStart,
            targetEnd = chosen.targetEnd,
            ipaContext = chosen.ipaContext ?: chunk.ipaContext
        )
    }
}
