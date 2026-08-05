package dev.ikna.data.pack

import android.content.Context
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.PackEntity
import kotlinx.serialization.json.Json

data class ImportResult(val packId: String, val installed: Int, val skipped: Int)

/**
 * Installs chunk packs shipped in assets, and imports packs the user brings in
 * from a file.
 *
 * Content is data, not code: a pack can be replaced wholesale without touching
 * card state, because cards are keyed by chunk id and chunks are upserted.
 */
class PackLoader(
    private val context: Context,
    private val chunkDao: ChunkDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun installBundledPacks() {
        val indexText = context.assets.open("packs/manifest.json")
            .bufferedReader().use { it.readText() }
        val index = json.decodeFromString<PackIndex>(indexText)
        for (manifest in index.packs) {
            val installed = chunkDao.pack(manifest.id)
            if (installed != null && installed.version >= manifest.version) continue
            install(manifest)
        }
    }

    private suspend fun install(manifest: PackManifest) {
        var count = 0
        context.assets.open("packs/" + manifest.file).bufferedReader().useLines { lines ->
            count = insertChunks(manifest.id, manifest.lang, lines)
        }
        chunkDao.upsertPack(
            PackEntity(
                id = manifest.id,
                version = manifest.version,
                lang = manifest.lang,
                chunkCount = if (count > 0) count else manifest.chunkCount,
                installedAt = System.currentTimeMillis(),
                title = manifest.title,
                isActive = chunkDao.pack(manifest.id)?.isActive ?: true
            )
        )
    }

    /**
     * Imports a `.jsonl` pack the user picked in the file browser. Same format
     * as the bundled packs, one chunk per line; unreadable lines are counted
     * and skipped instead of aborting the whole import.
     */
    suspend fun importJsonl(packId: String, title: String, lang: String, text: String): ImportResult {
        var skipped = 0
        val chunks = ArrayList<ChunkEntity>()
        val tokens = ArrayList<ChunkTokenEntity>()

        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parsed = runCatching { json.decodeFromString<PackChunk>(line) }.getOrNull()
            if (parsed == null) {
                skipped++
                continue
            }
            collect(packId, lang, parsed, chunks, tokens)
        }

        if (chunks.isNotEmpty()) chunkDao.upsertChunks(chunks)
        if (tokens.isNotEmpty()) chunkDao.upsertTokens(tokens)

        val existing = chunkDao.pack(packId)
        chunkDao.upsertPack(
            PackEntity(
                id = packId,
                version = (existing?.version ?: 0) + 1,
                lang = lang,
                chunkCount = chunks.size,
                installedAt = System.currentTimeMillis(),
                title = title,
                isActive = existing?.isActive ?: true
            )
        )
        return ImportResult(packId, chunks.size, skipped)
    }

    private suspend fun insertChunks(
        packId: String,
        lang: String,
        lines: Sequence<String>
    ): Int {
        val chunks = ArrayList<ChunkEntity>(256)
        val tokens = ArrayList<ChunkTokenEntity>(2048)
        var total = 0

        for (line in lines) {
            if (line.isBlank()) continue
            val c = json.decodeFromString<PackChunk>(line)
            collect(packId, lang, c, chunks, tokens)
            total++
            if (chunks.size >= 400) {
                chunkDao.upsertChunks(chunks); chunks.clear()
                chunkDao.upsertTokens(tokens); tokens.clear()
            }
        }
        if (chunks.isNotEmpty()) chunkDao.upsertChunks(chunks)
        if (tokens.isNotEmpty()) chunkDao.upsertTokens(tokens)
        return total
    }

    private fun collect(
        packId: String,
        lang: String,
        c: PackChunk,
        chunks: MutableList<ChunkEntity>,
        tokens: MutableList<ChunkTokenEntity>
    ) {
        chunks += ChunkEntity(
            id = c.id,
            packId = packId,
            lang = lang,
            text = c.text,
            contextSentence = c.context,
            translation = c.translation,
            targetStart = c.targetStart,
            targetEnd = c.targetEnd,
            freqRank = c.freqRank,
            audioRef = c.audioRef
        )
        c.tokens.forEachIndexed { i, t ->
            val inTarget = isInTarget(c, i)
            tokens += ChunkTokenEntity(
                chunkId = c.id,
                position = i,
                surface = t.surface,
                lemma = t.lemma,
                pos = t.pos,
                isTarget = inTarget,
                isContent = t.isContent,
                weight = weightFor(inTarget, t.isContent)
            )
        }
    }

    // Token positions are word indices into `context`; the target span is a
    // character range, so map words to characters once per chunk.
    private fun isInTarget(c: PackChunk, tokenIndex: Int): Boolean {
        var cursor = 0
        c.tokens.forEachIndexed { i, t ->
            val start = c.context.indexOf(t.surface, cursor).takeIf { it >= 0 } ?: cursor
            val end = start + t.surface.length
            if (i == tokenIndex) return start >= c.targetStart && end <= c.targetEnd
            cursor = end
        }
        return false
    }

    companion object {
        // The weighting that makes the component layer cheap and stable:
        // the trained span carries the answer, surrounding content words get a
        // fraction of the credit, function words get nothing at all.
        const val WEIGHT_TARGET = 1.0
        const val WEIGHT_CONTENT = 0.25
        const val WEIGHT_FUNCTION = 0.0

        fun weightFor(isTarget: Boolean, isContent: Boolean): Double = when {
            isTarget -> WEIGHT_TARGET
            isContent -> WEIGHT_CONTENT
            else -> WEIGHT_FUNCTION
        }
    }
}
