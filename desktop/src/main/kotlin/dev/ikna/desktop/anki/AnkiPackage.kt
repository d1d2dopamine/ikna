package dev.ikna.desktop.anki

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.github.luben.zstd.ZstdInputStream
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.inTransaction
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.pack.PackChunk
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.pack.SeedFormat
import dev.ikna.data.repo.RestoreRepository
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile

/** A finite, user-readable account of what crossed the bridge. */
data class AnkiImportResult(
    val decks: Int,
    val cards: Int,
    val reviewEventsImported: Int,
    val reviewEventsSkipped: Int,
    val replayedEvents: Int,
    val suspendedOrBuried: Int,
    val skippedCards: Int,
    val mediaCards: Int,
    val fallbackCards: Int,
    val historyWasLimited: Boolean,
    val collectionKind: String,
    /** What each deck was decided to be in, in the order the decks arrived. */
    val languages: List<String>
)

enum class AnkiImportError {
    FILE_TOO_LARGE,
    NOT_APKG,
    NO_COLLECTION,
    UNSUPPORTED_COLLECTION,
    UNREADABLE_DATABASE,
    NO_USABLE_CARDS,
    PLACEHOLDER_COLLECTION,
    FAILED
}

class AnkiImportException(
    val error: AnkiImportError,
    cause: Throwable? = null
) : Exception(error.name, cause)

/**
 * Reads an Anki package on the desktop and commits it as ordinary ikna data.
 *
 * The same importer the phone has, with two substitutions and nothing else: the
 * file arrives as a File rather than a content Uri, and the collection is read
 * through the bundled SQLite instead of android.database. Every decision about
 * what a card means -- which template renders it, how a cloze becomes a gap,
 * what language a deck is in, which ids it gets -- is the phone's, copied
 * deliberately rather than reinvented, because the deterministic ids are what
 * make importing the same .apkg on both machines produce the same decks.
 *
 * Parsing, rendering and validation all finish before the transaction opens. A
 * malformed package therefore leaves nothing behind: either the whole import
 * lands or the file is refused with a stated reason.
 */
class AnkiImporter(
    private val db: IknaDatabase,
    private val chunkDao: ChunkDao,
    private val packs: PackLoader,
    private val restore: RestoreRepository,
    private val cache: File
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importPackage(file: File, appLanguage: String): AnkiImportResult {
        val work = File(cache, "anki-import-" + UUID.randomUUID())
        if (!work.mkdirs()) throw AnkiImportException(AnkiImportError.FAILED)
        return try {
            val packageFile = File(work, "package.apkg")
            copyInto(file, packageFile)
            val collection = extractCollection(packageFile, work)
            val parsed = readCollection(collection.file, collection.kind, appLanguage)
            commit(parsed)
        } catch (known: AnkiImportException) {
            throw known
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.FAILED, problem)
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun commit(parsed: ParsedPackage): AnkiImportResult = db.inTransaction {
        var installed = 0
        for (deck in parsed.decks) {
            // A changed template may produce fewer tokens on re-import. Remove
            // only this deterministic pack's derived token rows, then rebuild
            // them from the newly rendered questions inside the same transaction.
            chunkDao.deleteTokensForPack(deck.packId)
            installed += packs.importChunks(
                packId = deck.packId,
                title = deck.title,
                lang = deck.lang,
                source = deck.cards,
                // A deck that has just arrived is off, like every other new
                // deck. Ten decks switching themselves on at once would rewrite
                // today's plan around material nobody has looked at yet.
                active = false
            ).installed
        }

        if (installed == 0) throw AnkiImportException(AnkiImportError.NO_USABLE_CARDS)

        val restored = if (parsed.reviews.isNotEmpty()) {
            val text = buildString {
                parsed.reviews.forEach { record ->
                    append(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), record))
                    append('\n')
                }
            }
            restore.restoreFromJsonl(text)
        } else {
            // Content changed even when a package has no scheduling history.
            // Make tomorrow's plan see it instead of keeping today's stale one.
            db.planDao().clear()
            null
        }

        AnkiImportResult(
            decks = parsed.decks.size,
            cards = installed,
            reviewEventsImported = restored?.imported ?: 0,
            reviewEventsSkipped = parsed.reviewRowsSkipped + (restored?.skipped ?: 0),
            replayedEvents = restored?.replayed ?: 0,
            suspendedOrBuried = parsed.suspendedOrBuried,
            skippedCards = parsed.skippedCards,
            mediaCards = parsed.mediaCards,
            fallbackCards = parsed.fallbackCards,
            historyWasLimited = parsed.historyWasLimited,
            collectionKind = parsed.kind,
            languages = parsed.decks.map { it.lang }
        )
    }

    private fun copyInto(source: File, destination: File) {
        if (!source.isFile) throw AnkiImportException(AnkiImportError.NOT_APKG)
        if (source.length() > MAX_PACKAGE_BYTES) {
            throw AnkiImportException(AnkiImportError.FILE_TOO_LARGE)
        }
        source.inputStream().use { input ->
            FileOutputStream(destination).use { output ->
                copyLimited(input, output, MAX_PACKAGE_BYTES)
            }
        }
        if (destination.length() < MIN_PACKAGE_BYTES) {
            throw AnkiImportException(AnkiImportError.NOT_APKG)
        }
    }

    private fun extractCollection(packageFile: File, work: File): ExtractedCollection {
        val collection = File(work, "collection.sqlite")
        try {
            ZipFile(packageFile).use { zip ->
                val entries = zip.entries().asSequence().toList()
                if (entries.size > MAX_ZIP_ENTRIES) {
                    throw AnkiImportException(AnkiImportError.NOT_APKG)
                }
                // Every member that could be a collection is tried in turn,
                // newest container first, and the first one that lands as a
                // readable SQLite file wins.
                val known = entries.filter { !it.isDirectory && it.name in COLLECTION_NAMES }
                    .sortedBy { COLLECTION_NAMES.indexOf(it.name) }
                val others = entries.filter {
                    !it.isDirectory &&
                        it.name.startsWith(COLLECTION_PREFIX) &&
                        it.name !in COLLECTION_NAMES
                }
                val candidates = known + others
                if (candidates.isEmpty()) {
                    throw AnkiImportException(AnkiImportError.NO_COLLECTION)
                }
                for (entry in candidates) {
                    if (entry.size > MAX_COLLECTION_BYTES) continue
                    val landed = runCatching {
                        BufferedInputStream(zip.getInputStream(entry)).use { input ->
                            input.mark(SQLITE_HEADER.size + 4)
                            val header = ByteArray(SQLITE_HEADER.size)
                            val read = input.read(header)
                            input.reset()
                            FileOutputStream(collection).use { output ->
                                when {
                                    read == SQLITE_HEADER.size &&
                                        header.contentEquals(SQLITE_HEADER) ->
                                        copyLimited(input, output, MAX_COLLECTION_BYTES)

                                    isZstd(header) -> ZstdInputStream(input).use { zstd ->
                                        copyLimited(zstd, output, MAX_COLLECTION_BYTES)
                                    }

                                    else -> throw AnkiImportException(
                                        AnkiImportError.UNSUPPORTED_COLLECTION
                                    )
                                }
                            }
                        }
                        hasSqliteHeader(collection)
                    }.getOrDefault(false)
                    if (landed) return ExtractedCollection(collection, entry.name)
                }
                throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
            }
        } catch (known: AnkiImportException) {
            throw known
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.NOT_APKG, problem)
        }
    }

    private fun copyLimited(input: InputStream, output: FileOutputStream, limit: Long) {
        val buffer = ByteArray(COPY_BUFFER)
        var written = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            written += count
            if (written > limit) throw AnkiImportException(AnkiImportError.FILE_TOO_LARGE)
            output.write(buffer, 0, count)
        }
        output.fd.sync()
    }

    private fun hasSqliteHeader(file: File): Boolean = file.inputStream().use { input ->
        val header = ByteArray(SQLITE_HEADER.size)
        input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun isZstd(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 0x28.toByte() && header[1] == 0xB5.toByte() &&
            header[2] == 0x2F.toByte() && header[3] == 0xFD.toByte()

    private fun readCollection(file: File, kind: String, appLanguage: String): ParsedPackage {
        // The bundled SQLite Room already uses, pointed at a copy of a file
        // written by another program. The copy is ours and is deleted with the
        // work directory, so the original .apkg is never opened for writing.
        val source = try {
            BundledSQLiteDriver().open(file.path)
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.UNREADABLE_DATABASE, problem)
        }
        return try {
            val sql = AnkiSql(source)
            val meta = readMeta(sql)
            // Which shape this collection is in is settled here, before anything
            // is written: the old JSON columns, or the tables a current Anki
            // writes instead.
            val shape = AnkiShape.read(sql, json, meta.models, meta.decks)
            val cards = readCards(
                sql = sql,
                collectionKey = meta.collectionKey,
                models = shape.models,
                deckNames = shape.deckNames,
                appLanguage = appLanguage
            )
            if (cards.decks.isEmpty()) {
                throw AnkiImportException(AnkiImportError.NO_USABLE_CARDS)
            }
            val reviews = readReviews(sql, cards.chunkByCardId)
            ParsedPackage(
                kind = kind,
                decks = cards.decks,
                reviews = reviews.records,
                reviewRowsSkipped = reviews.skipped,
                suspendedOrBuried = cards.suspendedOrBuried,
                skippedCards = cards.skippedCards,
                mediaCards = cards.mediaCards,
                fallbackCards = cards.fallbackCards,
                historyWasLimited = reviews.limited
            )
        } catch (known: AnkiImportException) {
            throw known
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.UNREADABLE_DATABASE, problem)
        } finally {
            runCatching { source.close() }
        }
    }

    /**
     * The creation date and the JSON columns, when the file still has them.
     *
     * Anki keeps the legacy `col` row for compatibility and leaves its JSON
     * columns empty; nothing promises a later version keeps the row at all.
     * Notetypes and decks come from the tables in that case, so this failing
     * costs a creation date, not the import.
     */
    private fun readMeta(sql: AnkiSql): CollectionMeta =
        runCatching { readColRow(sql) }.getOrNull() ?: CollectionMeta(1L, "", "")

    private fun readColRow(sql: AnkiSql): CollectionMeta {
        val row = sql.rows("SELECT crt, scm, models, decks FROM col LIMIT 1") { statement ->
            CollectionRow(
                crt = AnkiSql.long(statement, 0),
                scm = AnkiSql.long(statement, 1),
                models = AnkiSql.text(statement, 2),
                decks = AnkiSql.text(statement, 3)
            )
        }.firstOrNull() ?: throw AnkiImportException(AnkiImportError.UNREADABLE_DATABASE)
        if (row.models.length > MAX_META_CHARS || row.decks.length > MAX_META_CHARS) {
            throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
        }
        // The creation date is what every card id is counted from. A collection
        // that lost it still needs a key that is the same on every machine, so
        // the schema change time stands in, and a constant if that is gone too.
        val key = when {
            row.crt > 0 -> row.crt
            row.scm > 0 -> row.scm / 1_000L
            else -> 1L
        }
        return CollectionMeta(key, row.models, row.decks)
    }

    private fun readCards(
        sql: AnkiSql,
        collectionKey: Long,
        models: Map<Long, Model>,
        deckNames: Map<Long, String>,
        appLanguage: String
    ): CardRead {
        val suspended = sql.scalarLong("SELECT COUNT(*) FROM cards WHERE queue < 0").toInt()
        val activeTotal = sql.scalarLong("SELECT COUNT(*) FROM cards WHERE queue >= 0").toInt()
        val grouped = LinkedHashMap<Long, MutableList<PackChunk>>()
        val chunkByCard = HashMap<Long, String>()
        var skipped = (activeTotal - MAX_CARDS).coerceAtLeast(0)
        var mediaCards = 0
        var fallbackCards = 0
        var placeholders = 0
        var rank = 0

        val query = "SELECT c.id, c.ord, " +
            "CASE WHEN c.odid != 0 THEN c.odid ELSE c.did END AS deck_id, " +
            "n.mid, n.flds " +
            "FROM cards c JOIN notes n ON n.id = c.nid " +
            "WHERE c.queue >= 0 ORDER BY c.id LIMIT " + MAX_CARDS

        sql.rows(query, limit = MAX_CARDS) { statement ->
            val cardId = AnkiSql.long(statement, 0)
            val ordinal = AnkiSql.int(statement, 1)
            val deckId = AnkiSql.long(statement, 2)
            val model = models[AnkiSql.long(statement, 3)]
            val values = AnkiSql.text(statement, 4).split(FIELD_SEPARATOR)
            val fields = fieldsOf(model, values)

            // An occlusion note describes rectangles drawn over a picture.
            // Without the picture there is nothing to answer, so it is refused
            // and counted rather than stored as text about coordinates.
            if (AnkiText.isImageOcclusion(fields)) {
                skipped++
                return@rows null
            }

            // A cloze card already holds what this app marks by hand: a
            // sentence, and the exact phrase somebody chose to learn.
            val reading = if (model != null && model.cloze) {
                AnkiText.readCloze(fields, ordinal + 1)
            } else {
                null
            }
            val target = reading?.target
            var wholeText = ""
            var wholeMeaning = ""
            if (reading != null && target == null) {
                // The gap cannot be reconstructed. The card may still arrive
                // whole, but only if the note says somewhere what it means: a
                // sentence with its words already hidden and nothing to explain
                // them is worse than a card that never arrived, because it looks
                // real.
                wholeText = AnkiText.filledIn(fields)
                wholeMeaning = AnkiText.extraMeaning(fields)
                if (reading.shape != ClozeShape.MULTI_GAP ||
                    wholeText.isBlank() ||
                    wholeMeaning.isBlank()
                ) {
                    skipped++
                    return@rows null
                }
            }

            val rendered = render(model, values, ordinal)
            if (reading == null &&
                (!substantive(rendered.question) || !substantive(rendered.answer))
            ) {
                skipped++
                return@rows null
            }
            if (rendered.hadMedia) mediaCards++
            // A cloze card is read from its fields by design, so counting it as
            // recovered from fields would report a fault that is not one.
            if (rendered.usedFallback && reading == null) fallbackCards++

            val chunkId = stableChunkId(collectionKey, cardId)
            val chunk = if (target != null) {
                val context = target.context.take(AnkiText.MAX_SIDE_CHARS)
                val tokens = SeedFormat.tokens(context, target.start, target.end)
                if (tokens.isEmpty()) null else PackChunk(
                    id = chunkId,
                    text = context.substring(target.start, target.end),
                    context = context,
                    translation = AnkiText.extraMeaning(fields),
                    targetStart = target.start,
                    targetEnd = target.end,
                    freqRank = ++rank,
                    audioRef = null,
                    tokens = tokens
                )
            } else if (reading != null) {
                val context = wholeText.take(AnkiText.MAX_SIDE_CHARS)
                val tokens = SeedFormat.tokens(context, 0, context.length)
                if (tokens.isEmpty()) null else PackChunk(
                    id = chunkId,
                    text = context,
                    context = context,
                    translation = wholeMeaning,
                    targetStart = 0,
                    targetEnd = context.length,
                    freqRank = ++rank,
                    audioRef = null,
                    tokens = tokens
                )
            } else {
                val question = rendered.question.take(AnkiText.MAX_SIDE_CHARS)
                val answer = rendered.answer.take(AnkiText.MAX_SIDE_CHARS)
                val tokens = SeedFormat.tokens(question, 0, question.length)
                if (tokens.isEmpty()) null else PackChunk(
                    id = chunkId,
                    text = question,
                    context = question,
                    translation = answer,
                    targetStart = 0,
                    targetEnd = question.length,
                    freqRank = ++rank,
                    audioRef = null,
                    tokens = tokens
                )
            }
            if (chunk == null) {
                skipped++
                return@rows null
            }
            grouped.getOrPut(deckId) { ArrayList() } += chunk
            chunkByCard[cardId] = chunkId
            if (isPlaceholder(chunk.text) || isPlaceholder(chunk.translation)) placeholders++
            1
        }

        // An .apkg exported in the current format for an older reader carries a
        // decoy collection: one card asking the reader to update. Importing it
        // would leave a deck holding one sentence about Anki.
        if (chunkByCard.isNotEmpty() && placeholders == chunkByCard.size) {
            throw AnkiImportException(AnkiImportError.PLACEHOLDER_COLLECTION)
        }

        val decks = grouped.map { (deckId, chunks) ->
            val title = deckNames[deckId].orEmpty().ifBlank { "Anki deck " + deckId }
            ImportedDeck(
                packId = stablePackId(collectionKey, deckId),
                title = title,
                // Asked of the deck rather than of the person importing it. The
                // cards say what they are in, and every deck in one package can
                // say something different, which one question could not answer.
                lang = DeckLanguage.of(
                    deckName = title,
                    samples = chunks.take(DeckLanguage.MAX_SAMPLES)
                        .map { LanguageSample(it.context, it.translation) },
                    appLanguage = appLanguage
                ),
                cards = chunks
            )
        }
        return CardRead(decks, chunkByCard, suspended, skipped, mediaCards, fallbackCards)
    }

    /**
     * The answer history, oldest first, as ikna's own review log.
     *
     * Anki's intervals are not carried over -- its scheduler is not this one, so
     * a stability copied across would be a number pretending to mean something.
     * What is carried over is what actually happened: which card was answered,
     * when, and how it went. The schedule is then rebuilt from that history by
     * ikna's own scheduler, which is the only way the two can agree.
     *
     * Newest rows are read first and then reversed, so a history over the limit
     * keeps the recent past -- the part that decides what happens tomorrow --
     * instead of a beginning from years ago.
     */
    private fun readReviews(sql: AnkiSql, chunkByCardId: Map<Long, String>): ReviewRead {
        if (chunkByCardId.isEmpty()) return ReviewRead(emptyList(), 0, false)
        val total = sql.scalarLong(
            "SELECT COUNT(*) FROM revlog r JOIN cards c ON c.id = r.cid " +
                "WHERE c.queue >= 0 AND r.ease BETWEEN 1 AND 4"
        ).toInt()
        val limited = total > MAX_HISTORY_RECORDS
        val query = "SELECT r.id, r.cid, r.ease, r.time " +
            "FROM revlog r JOIN cards c ON c.id = r.cid " +
            "WHERE c.queue >= 0 AND r.ease BETWEEN 1 AND 4 " +
            "ORDER BY r.id DESC LIMIT " + MAX_HISTORY_RECORDS
        val newest = sql.rows(query, limit = MAX_HISTORY_RECORDS) { statement ->
            ReviewRow(
                id = AnkiSql.long(statement, 0),
                cardId = AnkiSql.long(statement, 1),
                rating = AnkiSql.int(statement, 2),
                durationMs = AnkiSql.long(statement, 3).coerceIn(0L, MAX_REVIEW_DURATION_MS)
            )
        }

        val previous = HashMap<Long, Long>()
        val reps = HashMap<Long, Int>()
        val records = ArrayList<ReviewRecord>(newest.size)
        var skipped = (total - newest.size).coerceAtLeast(0)
        for (row in newest.asReversed()) {
            val chunkId = chunkByCardId[row.cardId]
            if (chunkId == null) {
                skipped++
                continue
            }
            val before = previous[row.cardId]
            val count = reps[row.cardId] ?: 0
            records += ReviewRecord(
                id = row.id,
                chunkId = chunkId,
                level = 0,
                ts = row.id,
                rating = row.rating,
                elapsedDays = if (before == null) 0.0
                else (row.id - before).coerceAtLeast(0L) / DAY_MS,
                stabilityBefore = 1.0,
                stabilityAfter = 1.0,
                difficultyBefore = 5.0,
                difficultyAfter = 5.0,
                durationMs = row.durationMs,
                wasAmnesty = false,
                prevStability = 1.0,
                prevDifficulty = 5.0,
                prevDueAt = row.id,
                prevLastReviewAt = before,
                prevReps = count,
                prevLapses = 0,
                prevIsNew = before == null,
                prevInAmnesty = false
            )
            previous[row.cardId] = row.id
            reps[row.cardId] = count + 1
        }
        return ReviewRead(records, skipped, limited)
    }

    private fun render(model: Model?, values: List<String>, ordinal: Int): AnkiRenderedCard {
        if (model == null) return fallback(values, hadMedia = values.any(AnkiText::hasMedia))
        val fields = fieldsOf(model, values)
        val template = if (model.cloze) model.templates.firstOrNull()
        else model.templates.firstOrNull { it.ordinal == ordinal }
            ?: model.templates.getOrNull(ordinal)
            ?: model.templates.firstOrNull()
        if (template == null) return fallback(values, hadMedia = values.any(AnkiText::hasMedia))
        return AnkiText.render(
            questionTemplate = template.question,
            answerTemplate = template.answer,
            fields = fields,
            clozeNumber = if (model.cloze) ordinal + 1 else 1
        )
    }

    /**
     * The note field values by name.
     *
     * A note whose notetype is missing from the collection has no field names at
     * all, so numbered stand-ins are used instead: the card can still be read
     * from its values, and nothing downstream has to special-case a null model.
     */
    private fun fieldsOf(model: Model?, values: List<String>): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        if (model == null) {
            values.forEachIndexed { index, value -> fields["Field " + (index + 1)] = value }
            return fields
        }
        model.fields.forEachIndexed { index, name -> fields[name] = values.getOrElse(index) { "" } }
        return fields
    }

    private fun fallback(values: List<String>, hadMedia: Boolean): AnkiRenderedCard {
        val plain = values.map(AnkiText::plain).filter(::substantive)
        return AnkiRenderedCard(
            question = plain.firstOrNull().orEmpty(),
            answer = plain.drop(1).firstOrNull().orEmpty(),
            usedFallback = true,
            hadMedia = hadMedia
        )
    }

    private fun substantive(value: String): Boolean =
        AnkiText.usable(value.replace("[image]", "").replace("[audio]", ""))

    /**
     * The card Anki leaves behind for readers that cannot open its own format.
     *
     * Such a package holds two collections: the real one, and a decoy whose
     * single card reads "Please update to the latest Anki version, then import
     * the .colpkg/.apkg file again." The real one is preferred by name, so this
     * is the second line of defence: if everything a package yielded is that
     * sentence, the file is refused and says why.
     */
    private fun isPlaceholder(value: String): Boolean {
        val text = value.lowercase()
        return text.contains("latest anki version") || text.contains(".colpkg")
    }

    companion object {
        /**
         * Ceilings, not expectations. Each one exists because the alternative is
         * a window that stops responding or runs out of memory on a file it was
         * never going to be able to import anyway.
         */
        private const val MAX_PACKAGE_BYTES = 300L * 1024L * 1024L
        private const val MAX_COLLECTION_BYTES = 512L * 1024L * 1024L
        private const val MAX_CARDS = 50_000
        private const val MAX_HISTORY_RECORDS = 100_000
        private const val MAX_ZIP_ENTRIES = 20_000
        private const val MAX_META_CHARS = 32 * 1024 * 1024

        /** Smaller than the smallest possible zip: not a package at all. */
        private const val MIN_PACKAGE_BYTES = 256L

        private const val COPY_BUFFER = 64 * 1024
        private const val MAX_REVIEW_DURATION_MS = 120_000L
        private const val DAY_MS = 86_400_000.0
        private const val FIELD_SEPARATOR = '\u001F'

        /** Any other member named like a collection is still worth opening. */
        private const val COLLECTION_PREFIX = "collection.anki"

        private val COLLECTION_NAMES =
            listOf("collection.anki21b", "collection.anki21", "collection.anki2")

        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /**
         * Ids derived from the collection itself, not from this machine.
         *
         * This is what lets the same .apkg be imported on the phone and on the
         * computer and produce the same decks and the same cards, so a later
         * merge of the two has something to match on. It is also what makes
         * re-importing an updated export an update rather than a duplicate.
         */
        fun stableChunkId(collectionKey: Long, cardId: Long): String =
            "anki-" + collectionKey + "-card-" + cardId

        fun stablePackId(collectionKey: Long, deckId: Long): String =
            "anki-" + collectionKey + "-deck-" + deckId
    }
}

private data class ExtractedCollection(val file: File, val kind: String)
private data class CollectionRow(
    val crt: Long,
    val scm: Long,
    val models: String,
    val decks: String
)
private data class CollectionMeta(val collectionKey: Long, val models: String, val decks: String)
private data class ImportedDeck(
    val packId: String,
    val title: String,
    val lang: String,
    val cards: List<PackChunk>
)
private data class ReviewRow(val id: Long, val cardId: Long, val rating: Int, val durationMs: Long)
private data class CardRead(
    val decks: List<ImportedDeck>,
    val chunkByCardId: Map<Long, String>,
    val suspendedOrBuried: Int,
    val skippedCards: Int,
    val mediaCards: Int,
    val fallbackCards: Int
)
private data class ReviewRead(val records: List<ReviewRecord>, val skipped: Int, val limited: Boolean)
private data class ParsedPackage(
    val kind: String,
    val decks: List<ImportedDeck>,
    val reviews: List<ReviewRecord>,
    val reviewRowsSkipped: Int,
    val suspendedOrBuried: Int,
    val skippedCards: Int,
    val mediaCards: Int,
    val fallbackCards: Int,
    val historyWasLimited: Boolean
)
