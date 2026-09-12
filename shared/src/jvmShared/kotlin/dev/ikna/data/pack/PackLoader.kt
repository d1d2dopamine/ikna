package dev.ikna.data.pack

import dev.ikna.data.db.ChunkDao
import dev.ikna.platform.Assets
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkContextEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.PackEntity
import dev.ikna.data.db.PackChunkEntity
import dev.ikna.domain.session.Shapes
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ImportResult(val packId: String, val installed: Int, val skipped: Int)

/**
 * How long a deck's name is allowed to be.
 *
 * A catalogue title is written to be read in a list on a wide page. The deck
 * row shows it next to a menu, a switch and a progress bar, and a name that
 * wrapped onto a third line pushed the bar down out of its row and left every
 * row in the list a different height. So the cut is made once, here, on the way
 * into the database, instead of being repeated by every screen that draws a
 * deck -- and it is the stored name that is short, so it is also short when the
 * deck is exported or renamed.
 */
const val MAX_PACK_TITLE = 40

/**
 * A name cut to [MAX_PACK_TITLE], on a word boundary when there is one near the
 * end, because "English from Russian, beg\u2026" reads worse than "English from
 * Russian\u2026". A name with nothing in it falls back to the pack's identifier:
 * the deck list has no other handle on it.
 */
fun packTitle(raw: String?, packId: String): String {
    val trimmed = (raw ?: "").trim()
    if (trimmed.isEmpty()) return packId
    if (trimmed.length <= MAX_PACK_TITLE) return trimmed
    val cut = trimmed.take(MAX_PACK_TITLE)
    val space = cut.lastIndexOf(' ')
    val head = if (space >= MAX_PACK_TITLE / 2) cut.take(space) else cut
    return head.trimEnd(' ', ',', ';', '-', '\u2014', '\u00B7') + "\u2026"
}

/**
 * Installs chunk packs shipped in assets, and imports packs the user brings in
 * from a file.
 *
 * Content is data, not code: a pack can be replaced wholesale without touching
 * card state, because cards are keyed by chunk id and chunks are upserted.
 */
class PackLoader(
    private val assets: Assets,
    private val chunkDao: ChunkDao,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    suspend fun installBundledPacks() {
        val indexText = assets.open("packs/manifest.json")
            .bufferedReader().use { it.readText() }
        val index = json.decodeFromString<PackIndex>(indexText)
        for (manifest in index.packs) {
            val installed = chunkDao.pack(manifest.id)
            if (installed != null && installed.version >= manifest.version) continue
            // A deck named in the manifest whose file is not in this build is skipped
            // rather than fatal. The manifest is the list of what ships; a file missing
            // from it must cost the other decks nothing, and an empty first run is a
            // worse answer than one deck short.
            runCatching { install(manifest) }
        }
    }

    private suspend fun install(manifest: PackManifest) {
        var count = 0
        assets.open("packs/" + manifest.file).bufferedReader().useLines { lines ->
            count = insertChunks(manifest.id, manifest.lang, lines)
        }
        val existing = chunkDao.pack(manifest.id)
        chunkDao.upsertPack(
            PackEntity(
                id = manifest.id,
                version = manifest.version,
                lang = manifest.lang,
                chunkCount = if (count > 0) count else manifest.chunkCount,
                // When the deck first arrived, not when it was last written.
                // Progress through a deck is measured from this date, so a new
                // version of the same deck must not reset it.
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                title = packTitle(manifest.title, manifest.id),
                // A deck the user already switched keeps their choice.
                isActive = existing?.isActive ?: manifest.active
            )
        )
    }

    /** Imports a JSONL pack. Catalogue v2 lines are one target with contexts. */
    suspend fun importJsonl(packId: String, title: String, lang: String, text: String): ImportResult {
        var skipped = 0
        val chunks = ArrayList<ChunkEntity>()
        val tokens = ArrayList<ChunkTokenEntity>()
        val memberships = ArrayList<PackChunkEntity>()
        val contexts = ArrayList<ChunkContextEntity>()
        val shared = HashSet<String>()
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val parsed = runCatching { json.decodeFromString<PackChunk>(line) }.getOrNull()
            if (parsed == null) { skipped++; continue }
            collect(packId, lang, parsed, chunks, tokens, memberships, contexts, shared)
        }
        flushCollected(chunks, tokens, memberships, contexts, shared)
        val existing = chunkDao.pack(packId)
        val installed = memberships.map { it.chunkId }.distinct().size
        chunkDao.upsertPack(
            PackEntity(
                id = packId,
                version = (existing?.version ?: 0) + 1,
                lang = lang,
                chunkCount = installed,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                title = packTitle(title, packId),
                isActive = existing?.isActive ?: true
            )
        )
        return ImportResult(packId, installed, skipped)
    }

    /** Installs chunks built in memory through the same target/membership path. */
    suspend fun importChunks(
        packId: String,
        title: String,
        lang: String,
        source: List<PackChunk>,
        skipped: Int = 0,
        append: Boolean = false,
        active: Boolean = true
    ): ImportResult {
        val chunks = ArrayList<ChunkEntity>(source.size)
        val tokens = ArrayList<ChunkTokenEntity>(source.size * 8)
        val memberships = ArrayList<PackChunkEntity>(source.size)
        val contexts = ArrayList<ChunkContextEntity>(source.size * 2)
        val shared = HashSet<String>()
        for (c in source) collect(packId, lang, c, chunks, tokens, memberships, contexts, shared)
        flushCollected(chunks, tokens, memberships, contexts, shared)
        val existing = chunkDao.pack(packId)
        val added = memberships.map { it.chunkId }.distinct().size
        chunkDao.upsertPack(
            PackEntity(
                id = packId,
                version = (existing?.version ?: 0) + 1,
                lang = lang,
                chunkCount = if (append) (existing?.chunkCount ?: 0) + added else added,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                title = packTitle(title, packId),
                isActive = existing?.isActive ?: active
            )
        )
        return ImportResult(packId, added, skipped)
    }

    private suspend fun insertChunks(packId: String, lang: String, lines: Sequence<String>): Int {
        val chunks = ArrayList<ChunkEntity>(256)
        val tokens = ArrayList<ChunkTokenEntity>(2048)
        val memberships = ArrayList<PackChunkEntity>(256)
        val contexts = ArrayList<ChunkContextEntity>(768)
        val shared = HashSet<String>()
        var total = 0
        for (line in lines) {
            if (line.isBlank()) continue
            collect(packId, lang, json.decodeFromString<PackChunk>(line), chunks, tokens, memberships, contexts, shared)
            total++
            if (chunks.size >= 400) {
                flushCollected(chunks, tokens, memberships, contexts, shared)
                chunks.clear(); tokens.clear(); memberships.clear(); contexts.clear(); shared.clear()
            }
        }
        flushCollected(chunks, tokens, memberships, contexts, shared)
        return total
    }

    /**
     * The first installed Catalogue v2 occurrence becomes the target's current
     * primary representation. Later decks add membership and source contexts
     * without replacing the shared FSRS identity or component tokens.
     */
    private suspend fun flushCollected(
        chunks: List<ChunkEntity>,
        tokens: List<ChunkTokenEntity>,
        memberships: List<PackChunkEntity>,
        contexts: List<ChunkContextEntity>,
        shared: Set<String>
    ) {
        val sharedChunks = chunks.filter { it.id in shared }
        val legacyChunks = chunks.filterNot { it.id in shared }
        if (legacyChunks.isNotEmpty()) chunkDao.upsertChunks(legacyChunks)
        if (sharedChunks.isNotEmpty()) chunkDao.insertChunksIgnore(sharedChunks)
        val sharedTokens = tokens.filter { it.chunkId in shared }
        val legacyTokens = tokens.filterNot { it.chunkId in shared }
        if (legacyTokens.isNotEmpty()) chunkDao.upsertTokens(legacyTokens)
        if (sharedTokens.isNotEmpty()) chunkDao.insertTokensIgnore(sharedTokens)
        if (memberships.isNotEmpty()) chunkDao.upsertPackChunks(memberships)
        if (contexts.isNotEmpty()) chunkDao.upsertChunkContexts(contexts)
    }

    private fun collect(
        packId: String,
        lang: String,
        c: PackChunk,
        chunks: MutableList<ChunkEntity>,
        tokens: MutableList<ChunkTokenEntity>,
        memberships: MutableList<PackChunkEntity>,
        contexts: MutableList<ChunkContextEntity>,
        shared: MutableSet<String>
    ) {
        val chunkId = c.targetId ?: c.id
        if (c.targetId != null) shared += chunkId
        chunks += ChunkEntity(
            id = chunkId, packId = packId, lang = lang, text = c.text,
            contextSentence = c.context, translation = c.translation,
            targetStart = c.targetStart, targetEnd = c.targetEnd, freqRank = c.freqRank,
            audioRef = c.audioRef, ipa = c.ipa, ipaContext = c.ipaContext
        )
        memberships += PackChunkEntity(
            packId = packId, chunkId = chunkId, freqRank = c.freqRank,
            primaryContextId = c.contextId, primaryMeaningId = c.meaningId
        )
        val marked = Shapes.hasContext(c.context.length, c.targetStart, c.targetEnd)
        c.tokens.forEachIndexed { i, t ->
            val inTarget = marked && isInTarget(c, i)
            tokens += ChunkTokenEntity(
                chunkId = chunkId, position = i, surface = t.surface, lemma = t.lemma,
                pos = t.pos, isTarget = inTarget, isContent = t.isContent,
                weight = weightFor(inTarget, t.isContent)
            )
        }
        contexts += contextEntity(packId, chunkId, c)
        c.contexts.forEach { contexts += contextEntity(packId, chunkId, it) }
    }

    private fun contextEntity(packId: String, chunkId: String, c: PackChunk): ChunkContextEntity =
        ChunkContextEntity(
            packId = packId,
            chunkId = chunkId,
            contextId = c.contextId ?: "legacy:${c.id}",
            meaningId = c.meaningId ?: "legacy:${c.id}",
            sourceFamily = c.sourceFamily ?: "legacy",
            contextSentence = c.context,
            translation = c.translation,
            targetStart = c.targetStart,
            targetEnd = c.targetEnd,
            freqRank = c.freqRank,
            ipaContext = c.ipaContext,
            tokensJson = json.encodeToString(c.tokens)
        )

    private fun contextEntity(packId: String, chunkId: String, c: PackContext): ChunkContextEntity =
        ChunkContextEntity(
            packId = packId,
            chunkId = chunkId,
            contextId = c.contextId,
            meaningId = c.meaningId,
            sourceFamily = c.sourceFamily,
            contextSentence = c.context,
            translation = c.translation,
            targetStart = c.targetStart,
            targetEnd = c.targetEnd,
            freqRank = c.freqRank,
            ipaContext = c.ipaContext,
            tokensJson = json.encodeToString(c.tokens)
        )

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
