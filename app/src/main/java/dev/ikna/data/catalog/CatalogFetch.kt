package dev.ikna.data.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import dev.ikna.data.pack.PackChunk
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/*
 * The static requests the catalogue can make, and nothing else.
 *
 * Until this version the app opened one socket in its life, to ask the releases
 * page for a newer build. This adds a second of the same shape, and the shape is
 * the promise: a GET for a static file, no body, no cookie, no identifier, and
 * nothing about the person on the phone travelling in either direction. A deck
 * is downloaded because somebody tapped it, and the server learns what a web
 * server learns when a browser asks for a file.
 *
 * The index, a bounded preview prefix and the full deck all live in one release,
 * so every URL is fixed at build time. A deck's
 * address is its file name joined to that release -- never a URL out of the
 * index, because an index is data off the network and data off the network does
 * not get to choose which host this app downloads from.
 */

/** The release the pipeline publishes the catalogue into. */
const val CATALOG_BASE_URL: String =
    "https://github.com/d1d2dopamine/ikna/releases/download/catalog/"

/** The one file that lists everything. About a hundred kilobytes. */
const val CATALOG_INDEX_URL: String = CATALOG_BASE_URL + "index.json"

/** The page a person can read the same thing on, when the app cannot. */
const val CATALOG_PAGE_URL: String =
    "https://github.com/d1d2dopamine/ikna/releases/tag/catalog"

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000
private const val BUFFER_BYTES = 64 * 1024

/**
 * Ceilings, because a download with nowhere to stop is a way to fill a phone.
 * The index is a list of a few hundred decks; a deck of ten thousand chunks with
 * its token arrays is a couple of megabytes.
 */
private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
private const val MAX_DECK_BYTES = 24 * 1024 * 1024
private const val MAX_PREVIEW_BYTES = 96 * 1024
private const val PREVIEW_SCAN_LINES = 12
const val CATALOG_PREVIEW_COUNT = 3

/**
 * Turns a deck's file name into the address it is fetched from.
 *
 * The name is checked, not repaired. Stripping the characters a file name cannot
 * hold would quietly turn `https://elsewhere/x.jsonl` into a name that passes,
 * and a check that edits its input until it agrees is not a check. So anything
 * that is not a plain file name of ours -- a slash, a colon, a dot leading
 * nowhere, an extension that is not a deck -- is refused, and nothing is
 * fetched. The address itself is always built here, from our own release.
 */
fun catalogDeckUrl(fileName: String): String? {
    val name = fileName.trim()
    if (name.length < 3 || name.length > 120) return null
    if (!name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) {
        return null
    }
    if (name.startsWith(".") || name.contains("..")) return null
    if (!name.endsWith(".jsonl", ignoreCase = true)) return null
    return CATALOG_BASE_URL + name
}

/**
 * The catalogue over the network.
 *
 * Every failure arrives as null. There is no retry loop, no error code and no
 * message for the interface to explain: either the list is here or the screen
 * says it could not be fetched and offers the page in a browser.
 */
class CatalogFetch(
    private val installedVersion: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    /** The whole list, or null. */
    suspend fun index(): CatalogIndex? = withContext(Dispatchers.IO) {
        val text = runCatching { text(CATALOG_INDEX_URL, MAX_INDEX_BYTES) }.getOrNull()
            ?: return@withContext null
        val parsed = runCatching { json.decodeFromString<CatalogIndex>(text) }.getOrNull()
        // An index that parsed but holds nothing is the same event as no index:
        // there is nothing to draw either way, and "empty" would read as "this
        // app has no decks" rather than "the list did not arrive".
        if (parsed == null || parsed.decks.isEmpty()) null else parsed
    }

    /**
     * One deck's lines, as text, or null.
     *
     * [onProgress] is called with the bytes so far and the total, once per whole
     * percent, exactly as the updater's download reports itself -- the band on
     * screen is the same band, so it had better be fed the same way.
     *
     * The file is held in memory rather than written to the cache. A deck is a
     * couple of megabytes of text on its way into the database in one go; a file
     * on disk would be a second copy to delete afterwards and a half-written one
     * to explain when the network drops.
     */
    suspend fun deck(
        deck: CatalogDeck,
        onProgress: (read: Long, total: Long) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        val url = catalogDeckUrl(deck.file) ?: return@withContext null
        var connection: HttpURLConnection? = null
        try {
            connection = open(url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null

            val declared = connection.contentLengthLong
            val total = if (declared > 0L) declared else deck.sizeBytes
            if (total > MAX_DECK_BYTES) return@withContext null

            val text = StringBuilder()
            var read = 0L
            var shown = -1
            onProgress(0L, total)
            connection.inputStream.reader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(BUFFER_BYTES)
                while (true) {
                    // Leaving the screen has to stop the socket, not just stop
                    // looking at it.
                    if (!isActive) return@withContext null
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    read += count
                    if (read > MAX_DECK_BYTES) return@withContext null
                    val percent = progressPercentOf(read, total)
                    if (percent != shown) {
                        shown = percent
                        onProgress(read, total)
                    }
                }
            }
            if (text.isEmpty()) return@withContext null
            onProgress(read, total)
            text.toString()
        } catch (failed: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Three real cards before the full deck is requested.
     *
     * A Range request asks GitHub for only the beginning. Some HTTP mirrors
     * ignore ranges, so [prefix] also stops reading locally at 96 KiB and closes
     * the socket. A preview can therefore never turn into a hidden full download.
     */
    suspend fun preview(deck: CatalogDeck): List<CatalogPreviewCard>? =
        withContext(Dispatchers.IO) {
            val url = catalogDeckUrl(deck.file) ?: return@withContext null
            val text = runCatching { prefix(url, MAX_PREVIEW_BYTES) }.getOrNull()
                ?: return@withContext null
            parseCatalogPreview(text, CATALOG_PREVIEW_COUNT, json)
                .takeIf { it.isNotEmpty() }
        }

    private fun text(url: String, cap: Int): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val buffer = CharArray(8 * 1024)
            val text = StringBuilder()
            connection.inputStream.reader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    text.append(buffer, 0, read)
                    if (text.length > cap) return null
                }
            }
            return text.toString()
        } finally {
            connection?.disconnect()
        }
    }

    private fun prefix(url: String, cap: Int): String? {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url).apply {
                setRequestProperty("Range", "bytes=0-${cap - 1}")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                return null
            }

            val bytes = ByteArrayOutputStream(cap)
            val buffer = ByteArray(8 * 1024)
            var lines = 0
            connection.inputStream.use { input ->
                while (bytes.size() < cap && lines < PREVIEW_SCAN_LINES) {
                    val wanted = minOf(buffer.size, cap - bytes.size())
                    val read = input.read(buffer, 0, wanted)
                    if (read <= 0) break
                    bytes.write(buffer, 0, read)
                    for (i in 0 until read) if (buffer[i] == '\n'.code.toByte()) lines++
                }
            }
            return bytes.toString(Charsets.UTF_8.name()).takeIf { it.isNotBlank() }
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain")
            // The version is here for the same reason it is on the update check:
            // so a broken catalogue can be recognised in a log, not so anybody
            // can be counted.
            setRequestProperty("User-Agent", "ikna/" + installedVersion)
        }
}

/** Pure parser kept outside the socket so malformed-prefix cases are unit tested. */
fun parseCatalogPreview(
    text: String,
    limit: Int = CATALOG_PREVIEW_COUNT,
    json: Json = Json { ignoreUnknownKeys = true }
): List<CatalogPreviewCard> {
    if (limit <= 0) return emptyList()
    val cards = ArrayList<CatalogPreviewCard>(limit)
    for (line in text.lineSequence().take(PREVIEW_SCAN_LINES)) {
        if (line.isBlank()) continue
        val card = runCatching { json.decodeFromString<PackChunk>(line) }.getOrNull() ?: continue
        if (card.text.isBlank() || card.context.isBlank()) continue
        val meaning = catalogMeaning(card.translation)
        cards += CatalogPreviewCard(
            text = card.text,
            context = card.context,
            translation = meaning.text,
            tatoebaId = meaning.tatoebaId
        )
        if (cards.size == limit) break
    }
    return cards
}

/**
 * The number beside the band, 0 to 100.
 *
 * The same arithmetic the updater uses, spelled out here rather than imported,
 * because a deck download reporting characters and an APK download reporting
 * bytes are two different totals and only one of them may ever be called "the
 * size of the file".
 */
fun progressPercentOf(read: Long, total: Long): Int {
    if (total <= 0L || read <= 0L) return 0
    val fraction = (read.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
    return (fraction * 100.0).toInt().coerceIn(0, 100)
}

/** How full the band is drawn. Unknown length means nothing is drawn. */
fun progressFractionOf(read: Long, total: Long): Float {
    if (total <= 0L || read <= 0L) return 0f
    return (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
