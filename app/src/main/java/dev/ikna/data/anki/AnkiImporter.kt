package dev.ikna.data.anki

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.withTransaction
import com.github.luben.zstd.ZstdInputStream
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.pack.PackChunk
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.pack.SeedFormat
import dev.ikna.data.repo.RestoreRepository
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    val collectionKind: String
)

enum class AnkiImportError {
    FILE_TOO_LARGE,
    NOT_APKG,
    NO_COLLECTION,
    UNSUPPORTED_COLLECTION,
    UNREADABLE_DATABASE,
    NO_USABLE_CARDS,
    FAILED
}

class AnkiImportException(
    val error: AnkiImportError,
    cause: Throwable? = null
) : Exception(error.name, cause)

/**
 * Reads an Anki package locally and commits it as ordinary ikna data.
 *
 * Parsing, template rendering and validation finish before the Room transaction
 * starts. Packs, review rows, derived schedules and the daily plan are then one
 * transaction: a malformed package leaves no half-deck behind. Re-import uses
 * deterministic ids and RestoreRepository's existing review signatures, so it
 * updates text and skips history that is already present.
 */
class AnkiImporter(
    private val context: Context,
    private val db: IknaDatabase,
    private val chunkDao: ChunkDao,
    private val packs: PackLoader,
    private val restore: RestoreRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importPackage(uri: Uri, lang: String): AnkiImportResult {
        val work = File(context.cacheDir, "anki-import-" + UUID.randomUUID())
        if (!work.mkdirs()) throw AnkiImportException(AnkiImportError.FAILED)
        return try {
            val packageFile = File(work, "package.apkg")
            copyUri(uri, packageFile)
            val collection = extractCollection(packageFile, work)
            val parsed = readCollection(collection.file, collection.kind, lang)
            commit(parsed)
        } catch (known: AnkiImportException) {
            throw known
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.FAILED, problem)
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun commit(parsed: ParsedPackage): AnkiImportResult = db.withTransaction {
        var installed = 0
        for (deck in parsed.decks) {
            // A changed template may produce fewer tokens on re-import. Remove
            // only this deterministic pack's derived token rows, then rebuild
            // them from the newly rendered questions inside the same transaction.
            chunkDao.deleteTokensForPack(deck.packId)
            installed += packs.importChunks(
                packId = deck.packId,
                title = deck.title,
                lang = parsed.lang,
                source = deck.cards
            ).installed
        }

        if (installed == 0) throw AnkiImportException(AnkiImportError.NO_USABLE_CARDS)

        val restored = if (parsed.reviews.isNotEmpty()) {
            val text = buildString {
                parsed.reviews.forEach { record ->
                    append(ReviewRecord.json.encodeToString(record))
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
            collectionKind = parsed.kind
        )
    }

    private fun copyUri(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destination).use { output ->
                copyLimited(input, output, MAX_PACKAGE_BYTES)
            }
        } ?: throw AnkiImportException(AnkiImportError.NOT_APKG)
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
                val entry = COLLECTION_NAMES.firstNotNullOfOrNull { name -> zip.getEntry(name) }
                    ?: throw AnkiImportException(AnkiImportError.NO_COLLECTION)
                if (entry.isDirectory || entry.size > MAX_COLLECTION_BYTES) {
                    throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
                }

                val buffered = BufferedInputStream(zip.getInputStream(entry))
                buffered.use { input ->
                    input.mark(SQLITE_HEADER.size + 4)
                    val header = ByteArray(SQLITE_HEADER.size)
                    val read = input.read(header)
                    input.reset()
                    FileOutputStream(collection).use { output ->
                        when {
                            read == SQLITE_HEADER.size && header.contentEquals(SQLITE_HEADER) ->
                                copyLimited(input, output, MAX_COLLECTION_BYTES)
                            isZstd(header) -> ZstdInputStream(input).use { zstd ->
                                copyLimited(zstd, output, MAX_COLLECTION_BYTES)
                            }
                            else -> throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
                        }
                    }
                }
                if (!hasSqliteHeader(collection)) {
                    throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
                }
                return ExtractedCollection(collection, entry.name)
            }
        } catch (known: AnkiImportException) {
            throw known
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.NOT_APKG, problem)
        }
    }

    private fun readCollection(file: File, kind: String, lang: String): ParsedPackage {
        val source = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (problem: Throwable) {
            throw AnkiImportException(AnkiImportError.UNREADABLE_DATABASE, problem)
        }
        return try {
            val meta = readMeta(source)
            val models = parseModels(meta.models)
            val deckNames = parseDecks(meta.decks)
            val cards = readCards(source, meta.collectionKey, models, deckNames)
            if (cards.decks.isEmpty()) {
                throw AnkiImportException(AnkiImportError.NO_USABLE_CARDS)
            }
            val reviews = readReviews(source, cards.chunkByCardId)
            ParsedPackage(
                lang = lang,
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
            source.close()
        }
    }

    private fun readMeta(database: SQLiteDatabase): CollectionMeta {
        database.rawQuery("SELECT crt, scm, models, decks FROM col LIMIT 1", null).use { cursor ->
            if (!cursor.moveToFirst()) throw AnkiImportException(AnkiImportError.UNREADABLE_DATABASE)
            val crt = cursor.getLong(0)
            val scm = cursor.getLong(1)
            val models = cursor.getString(2).orEmpty()
            val decks = cursor.getString(3).orEmpty()
            if (models.length > MAX_META_CHARS || decks.length > MAX_META_CHARS) {
                throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
            }
            val key = when {
                crt > 0 -> crt
                scm > 0 -> scm / 1_000L
                else -> 1L
            }
            return CollectionMeta(key, models, decks)
        }
    }

    private fun readCards(
        database: SQLiteDatabase,
        collectionKey: Long,
        models: Map<Long, Model>,
        deckNames: Map<Long, String>
    ): CardRead {
        val suspended = scalarLong(database, "SELECT COUNT(*) FROM cards WHERE queue < 0").toInt()
        val activeTotal = scalarLong(database, "SELECT COUNT(*) FROM cards WHERE queue >= 0").toInt()
        val grouped = LinkedHashMap<Long, MutableList<PackChunk>>()
        val chunkByCard = HashMap<Long, String>()
        var skipped = (activeTotal - MAX_CARDS).coerceAtLeast(0)
        var mediaCards = 0
        var fallbackCards = 0
        var rank = 0

        val sql = """
            SELECT c.id, c.ord,
                   CASE WHEN c.odid != 0 THEN c.odid ELSE c.did END AS deck_id,
                   n.mid, n.flds
            FROM cards c
            JOIN notes n ON n.id = c.nid
            WHERE c.queue >= 0
            ORDER BY c.id
            LIMIT $MAX_CARDS
        """.trimIndent()

        database.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                val cardId = cursor.getLong(0)
                val ordinal = cursor.getInt(1)
                val deckId = cursor.getLong(2)
                val model = models[cursor.getLong(3)]
                val values = cursor.getString(4).orEmpty().split(FIELD_SEPARATOR)
                val rendered = render(model, values, ordinal)
                if (!substantive(rendered.question) || !substantive(rendered.answer)) {
                    skipped++
                    continue
                }
                if (rendered.hadMedia) mediaCards++
                if (rendered.usedFallback) fallbackCards++

                val chunkId = stableChunkId(collectionKey, cardId)
                val question = rendered.question.take(AnkiText.MAX_SIDE_CHARS)
                val answer = rendered.answer.take(AnkiText.MAX_SIDE_CHARS)
                val tokens = SeedFormat.tokens(question, 0, question.length)
                if (tokens.isEmpty()) {
                    skipped++
                    continue
                }
                grouped.getOrPut(deckId) { ArrayList() } += PackChunk(
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
                chunkByCard[cardId] = chunkId
            }
        }

        val decks = grouped.map { (deckId, chunks) ->
            ImportedDeck(
                packId = stablePackId(collectionKey, deckId),
                title = deckNames[deckId].orEmpty().ifBlank { "Anki deck $deckId" },
                cards = chunks
            )
        }
        return CardRead(decks, chunkByCard, suspended, skipped, mediaCards, fallbackCards)
    }

    private fun readReviews(
        database: SQLiteDatabase,
        chunkByCardId: Map<Long, String>
    ): ReviewRead {
        if (chunkByCardId.isEmpty()) return ReviewRead(emptyList(), 0, false)
        val total = scalarLong(
            database,
            "SELECT COUNT(*) FROM revlog r JOIN cards c ON c.id = r.cid " +
                "WHERE c.queue >= 0 AND r.ease BETWEEN 1 AND 4"
        ).toInt()
        val limited = total > MAX_HISTORY_RECORDS
        val newest = ArrayList<ReviewRow>(minOf(total, MAX_HISTORY_RECORDS))
        val sql = """
            SELECT r.id, r.cid, r.ease, r.time
            FROM revlog r
            JOIN cards c ON c.id = r.cid
            WHERE c.queue >= 0 AND r.ease BETWEEN 1 AND 4
            ORDER BY r.id DESC
            LIMIT $MAX_HISTORY_RECORDS
        """.trimIndent()
        database.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                newest += ReviewRow(
                    id = cursor.getLong(0),
                    cardId = cursor.getLong(1),
                    rating = cursor.getInt(2),
                    durationMs = cursor.getLong(3).coerceIn(0L, MAX_REVIEW_DURATION_MS)
                )
            }
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
                elapsedDays = if (before == null) 0.0 else (row.id - before).coerceAtLeast(0L) / DAY_MS,
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
        val fields = LinkedHashMap<String, String>()
        model.fields.forEachIndexed { index, name -> fields[name] = values.getOrElse(index) { "" } }
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

    private fun parseModels(raw: String): Map<Long, Model> {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
        return root.mapNotNull model@ { (id, element) ->
            val body = element as? JsonObject ?: return@model null
            val fields = (body["flds"] as? JsonArray)?.mapNotNull field@ { field ->
                (field as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            }.orEmpty()
            val templates = (body["tmpls"] as? JsonArray)?.mapNotNull template@ { item ->
                val template = item as? JsonObject ?: return@template null
                Template(
                    ordinal = template["ord"]?.jsonPrimitive?.intOrNull ?: 0,
                    question = template["qfmt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    answer = template["afmt"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            }.orEmpty()
            id.toLongOrNull()?.let { key ->
                key to Model(
                    fields = fields,
                    templates = templates,
                    cloze = body["type"]?.jsonPrimitive?.intOrNull == 1
                )
            }
        }.toMap()
    }

    private fun parseDecks(raw: String): Map<Long, String> {
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw AnkiImportException(AnkiImportError.UNSUPPORTED_COLLECTION)
        return root.mapNotNull { (id, element) ->
            val body = element as? JsonObject ?: return@mapNotNull null
            val name = body["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            id.toLongOrNull()?.let { it to name }
        }.toMap()
    }

    private fun scalarLong(database: SQLiteDatabase, query: String): Long =
        database.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
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

    companion object {
        const val MAX_PACKAGE_BYTES = 300L * 1024L * 1024L
        const val MAX_COLLECTION_BYTES = 512L * 1024L * 1024L
        const val MAX_CARDS = 50_000
        const val MAX_HISTORY_RECORDS = 100_000
        const val MAX_ZIP_ENTRIES = 20_000
        private const val MAX_META_CHARS = 32 * 1024 * 1024
        private const val MIN_PACKAGE_BYTES = 256L
        private const val COPY_BUFFER = 64 * 1024
        private const val MAX_REVIEW_DURATION_MS = 120_000L
        private const val DAY_MS = 86_400_000.0
        private const val FIELD_SEPARATOR = '\u001F'
        private val COLLECTION_NAMES = listOf("collection.anki21b", "collection.anki21", "collection.anki2")
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        fun stableChunkId(collectionKey: Long, cardId: Long): String =
            "anki-$collectionKey-card-$cardId"

        fun stablePackId(collectionKey: Long, deckId: Long): String =
            "anki-$collectionKey-deck-$deckId"
    }
}

private data class ExtractedCollection(val file: File, val kind: String)
private data class CollectionMeta(val collectionKey: Long, val models: String, val decks: String)
private data class Template(val ordinal: Int, val question: String, val answer: String)
private data class Model(val fields: List<String>, val templates: List<Template>, val cloze: Boolean)
private data class ImportedDeck(val packId: String, val title: String, val cards: List<PackChunk>)
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
    val lang: String,
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
