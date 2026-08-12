package dev.ikna.data.repo

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.pack.ImportResult
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.pack.SeedFormat
import dev.ikna.data.pack.SeedLineProblem

/**
 * What a deck starts as when nobody has said which language it is in.
 *
 * A file says nothing about the language inside it, and guessing from the
 * alphabet gets English and Polish wrong in opposite directions. No language
 * means no voice, so this value is why a deck made by the user used to be silent
 * -- which is the reason the import now asks.
 */
internal const val NO_LANG = "custom"

/**
 * What an import did, in enough detail to say it out loud.
 *
 * The old import reported two numbers and nothing else, so a file that produced
 * nothing produced no explanation either — and a file written by hand, or by a
 * model that wandered off the format, is exactly the file that produces nothing.
 * [firstProblem] is the first line that did not make it and why, which is the
 * one piece of information that lets someone fix their file.
 */
data class DeckImport(
    val packId: String,
    val installed: Int,
    val skipped: Int,
    val firstProblem: SeedLineProblem?
)

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

    /** One deck, for its own screen. Null once it has been deleted. */
    suspend fun deck(id: String): DeckSummary? = chunkDao.pack(id)?.let { pack ->
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

    /**
     * Which language a deck is in.
     *
     * Asked once while the deck is being imported, and changeable here for as
     * long as the deck exists. A deck that arrived before the import asked
     * carries [NO_LANG] and cannot be read aloud, so this row is not only for
     * people who changed their mind.
     */
    suspend fun setLang(id: String, lang: String) = chunkDao.setPackLang(id, lang)

    /**
     * Deletes a deck: its cards, its tokens, its chunks, and the pack row.
     *
     * The answers in `reviews` stay. That table is append-only and it is the one
     * irreplaceable thing in the database - the statistics are computed from it -
     * so tidying up a deck must not cost months of history. Chunk ids are derived
     * from the deck id and the line number, so re-importing the same file later
     * lines the old answers back up with it.
     */
    suspend fun delete(id: String) {
        chunkDao.deleteCardsForPack(id)
        chunkDao.deleteTokensForPack(id)
        chunkDao.deleteChunksForPack(id)
        chunkDao.deletePack(id)
    }

    /**
     * Imports a `.jsonl` pack picked in the system file browser.
     *
     * @param fallbackTitle what to call a deck whose file name is only an
     *   extension. Passed in rather than read from the string catalogue here:
     *   the catalogue is UI state, and a repository that reaches up into it
     *   cannot be tested without it and quietly bakes the language into stored
     *   data. The caller is on screen and already knows the language.
     * @param lang which language the cards are in, used by the voice and nothing
     *   else. Defaults to [NO_LANG]: a file cannot be asked, but the screen that
     *   imported it can, and does.
     */
    suspend fun importFile(
        fileName: String,
        text: String,
        fallbackTitle: String,
        lang: String = NO_LANG
    ): ImportResult {
        val stem = fileName.substringBeforeLast('.')
        return packLoader.importJsonl(
            packId = packIdFor(stem),
            title = stem.ifEmpty { fallbackTitle },
            lang = lang,
            text = text
        )
    }

    /**
     * Imports whatever the user brought, in whichever of the two shapes it is in.
     *
     * A deck used to have to be `.jsonl`: one JSON object per line, with token
     * arrays and character offsets in it. That is a format for a generator, not
     * for a person, and it made adding a deck the hardest thing in the app —
     * which for an app aimed at people who struggle to start is the worst
     * possible place to put the difficulty. The three-column format is the one
     * a model can be asked for and a person can read; `.jsonl` still imports so
     * that packs made with the tool in `tools/genpack` keep working.
     *
     * The shape is decided by looking at the text, not at the file name, because
     * a file arrives named anything at all and text pasted into the field has no
     * name in the first place.
     */
    suspend fun importText(
        fileName: String,
        text: String,
        fallbackTitle: String,
        lang: String = NO_LANG
    ): DeckImport {
        val stem = fileName.substringBeforeLast('.')
        val packId = packIdFor(stem)
        val title = stem.ifEmpty { fallbackTitle }

        if (SeedFormat.looksLikeJsonl(text)) {
            val result = packLoader.importJsonl(packId, title, lang, text)
            return DeckImport(packId, result.installed, result.skipped, null)
        }

        val parse = SeedFormat.parse(text)
        val result = packLoader.importChunks(
            packId = packId,
            title = title,
            lang = lang,
            source = SeedFormat.chunks(packId, parse.rows),
            skipped = parse.problems.size
        )
        return DeckImport(
            packId = packId,
            installed = result.installed,
            skipped = parse.problems.size,
            firstProblem = parse.problems.firstOrNull()
        )
    }

    /**
     * One deck per name. Importing the same file twice replaces the deck rather
     * than growing a second copy of it beside the first, and because cards are
     * keyed by chunk id, everything already learned in it survives the replacement.
     */
    /**
     * A deck as text, in the same three columns the importer accepts.
     *
     * Sharing a deck is the only way content moves between two people here:
     * there is no account, no server and no permission to reach one. What comes
     * out is what a person could have typed, so the person receiving it can read
     * it, edit it, or feed it to a model, instead of trusting an opaque blob.
     *
     * A bar inside a field would split a column that is not meant to split, so
     * it is replaced by a slash rather than the line being dropped.
     */
    suspend fun exportText(packId: String): String =
        chunkDao.chunksForPack(packId).joinToString("\n") { c ->
            listOf(c.text, c.contextSentence, c.translation)
                .joinToString("|") { field ->
                    field.replace('|', '/').replace('\n', ' ').trim()
                }
        }

    private fun packIdFor(stem: String): String {
        val slug = stem.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "pack" }
        return "user-" + slug
    }

    companion object {
        /** "Known" for the deck counter: three weeks of predicted stability. */
        const val KNOWN_STABILITY_DAYS = 21.0
    }
}
