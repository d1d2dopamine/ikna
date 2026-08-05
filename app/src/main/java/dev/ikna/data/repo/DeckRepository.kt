package dev.ikna.data.repo

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.pack.ImportResult
import dev.ikna.data.pack.PackLoader

data class DeckSummary(
    val id: String,
    val title: String,
    val lang: String,
    val total: Int,
    val introduced: Int,
    val known: Int,
    val isActive: Boolean
)

/**
 * The Decks screen.
 *
 * Turning a deck off only stops NEW chunks coming from it. Everything already
 * started keeps its schedule and its history, so switching decks is never a
 * decision with consequences — which is the point, because a decision with
 * consequences is a decision the user will avoid making.
 *
 * The daily load budget is shared across all active decks. Two active decks do
 * not mean twice the reviews; they mean the same budget drawn from two pools.
 */
class DeckRepository(
    private val chunkDao: ChunkDao,
    private val packLoader: PackLoader
) {

    suspend fun decks(): List<DeckSummary> = chunkDao.packs().map { pack ->
        DeckSummary(
            id = pack.id,
            title = pack.title ?: pack.id,
            lang = pack.lang,
            total = chunkDao.chunkCountFor(pack.id),
            introduced = chunkDao.introducedCountFor(pack.id),
            known = chunkDao.knownCountFor(pack.id, KNOWN_STABILITY_DAYS),
            isActive = pack.isActive
        )
    }

    suspend fun setActive(id: String, active: Boolean) = chunkDao.setPackActive(id, active)

    /** Imports a `.jsonl` pack picked in the system file browser. */
    suspend fun importFile(fileName: String, text: String): ImportResult {
        val stem = fileName.substringBeforeLast('.')
        val slug = stem.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "pack" }
        return packLoader.importJsonl(
            packId = "user-" + slug,
            title = stem.ifEmpty { "Свой набор" },
            lang = "custom",
            text = text
        )
    }

    companion object {
        /** "Known" for the deck counter: three weeks of predicted stability. */
        const val KNOWN_STABILITY_DAYS = 21.0
    }
}
