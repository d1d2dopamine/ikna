package dev.ikna.data.catalog

/*
 * Everything the catalogue screen decides, decided here.
 *
 * No Android in this file and no network either: the index arrives as a value,
 * the filters are values, and what comes out is a list. That is what makes it
 * testable, and CatalogFilterTest is why the screen can be as thin as it is.
 *
 * The ordering is the one rule worth reading twice. Decks are sorted by how much
 * is in them, largest first, because a catalogue is read top to bottom and the
 * deck somebody wants is almost always the biggest one for their pair. Ties fall
 * back to the title so the list never reshuffles itself between two openings.
 */

/** What the screen is filtering by. Empty strings mean "no filter". */
data class CatalogFilter(
    val lang: String = "",
    val meaningLang: String = "",
    val subject: String = "",
    val level: String = "",
    val query: String = "",
    val collection: String = ""
)

/**
 * Effective Catalogue v2 collection for a deck.
 * Legacy v1 decks omit [CatalogDeck.collection] and belong to [COLLECTION_EVERYDAY].
 */
fun deckCollectionOf(deck: CatalogDeck): String =
    deck.collection.trim().lowercase().ifEmpty { COLLECTION_EVERYDAY }

/** Number of decks available inside one collection (or the whole index when empty). */
fun collectionDeckCount(index: CatalogIndex, collection: String): Int {
    val wanted = collection.trim().lowercase()
    if (wanted.isEmpty()) return index.decks.size
    return index.decks.count { deckCollectionOf(it) == wanted }
}

private fun matchesCollection(deck: CatalogDeck, collection: String): Boolean {
    val wanted = collection.trim().lowercase()
    return wanted.isEmpty() || deckCollectionOf(deck) == wanted
}

/** The languages there is anything at all to learn in, in a stable order. */
fun learnableLangs(index: CatalogIndex, collection: String = ""): List<String> =
    index.decks
        .filter { matchesCollection(it, collection) }
        .map { it.lang }
        .distinct()
        .sorted()

/**
 * The languages the meanings can be in, for one language being learned.
 *
 * Asked in that order on purpose: somebody picks what they want to learn first
 * and what they already know second, and the second list is short.
 */
fun meaningLangsFor(index: CatalogIndex, lang: String, collection: String = ""): List<String> =
    index.decks
        .filter { matchesCollection(it, collection) }
        .filter { lang.isEmpty() || it.lang == lang }
        .map { it.meaningLang }
        .distinct()
        .sorted()

/** The topics present in the decks a filter already narrowed down to. */
fun subjectsFor(index: CatalogIndex, filter: CatalogFilter): List<String> =
    index.decks
        .filter { matchesLangs(it, filter) }
        .map { it.subject }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

/** The levels present in the decks a filter already narrowed down to. */
fun levelsFor(index: CatalogIndex, filter: CatalogFilter): List<String> {
    val present = index.decks
        .filter { matchesLangs(it, filter) }
        .filter { filter.subject.isEmpty() || it.subject == filter.subject }
        .map { it.level }
        .filter { it.isNotEmpty() }
        .distinct()
    return LEVEL_ORDER.filter { present.contains(it) } + present.filter { !LEVEL_ORDER.contains(it) }.sorted()
}

/**
 * Keep selections that are still offered, clearing unavailable dependent values
 * in question order. Search and collection are deliberate choices and are never silently reset.
 */
fun normalizeCatalogFilter(index: CatalogIndex, filter: CatalogFilter): CatalogFilter {
    var current = filter.copy(lang = filter.lang.takeIf { it in learnableLangs(index, filter.collection) }.orEmpty())
    current = current.copy(meaningLang = current.meaningLang
        .takeIf { it in meaningLangsFor(index, current.lang, current.collection) }.orEmpty())
    current = current.copy(subject = current.subject
        .takeIf { it in subjectsFor(index, current) }.orEmpty())
    return current.copy(level = current.level
        .takeIf { it in levelsFor(index, current) }.orEmpty())
}

/** The decks a filter leaves, in the order they are drawn. */
fun decksFor(index: CatalogIndex, filter: CatalogFilter): List<CatalogDeck> =
    index.decks
        .filter { matches(it, filter) }
        .sortedWith(
            compareByDescending<CatalogDeck> { it.chunkCount }
                .thenBy { it.title.lowercase() }
                .thenBy { it.id }
        )

private fun matchesLangs(deck: CatalogDeck, filter: CatalogFilter): Boolean {
    if (!matchesCollection(deck, filter.collection)) return false
    if (filter.lang.isNotEmpty() && deck.lang != filter.lang) return false
    if (filter.meaningLang.isNotEmpty() && deck.meaningLang != filter.meaningLang) return false
    return true
}

fun matches(deck: CatalogDeck, filter: CatalogFilter): Boolean {
    if (!matchesLangs(deck, filter)) return false
    if (filter.subject.isNotEmpty() && deck.subject != filter.subject) return false
    if (filter.level.isNotEmpty() && deck.level != filter.level) return false
    val query = filter.query.trim()
    if (query.isEmpty()) return true
    // Searched against what is on the row and nothing hidden: the title, the
    // topic, and the identifier somebody may have been given by a person rather
    // than by the screen.
    return listOf(deck.title, deck.subject, deck.id)
        .any { it.contains(query, ignoreCase = true) }
}

/**
 * How well one pair is served, as the pipeline measured it.
 *
 * A pair with no row is not thin, it is absent, and the screen says so with a
 * different sentence -- which is why this returns null rather than THIN.
 */
fun tierOf(
    index: CatalogIndex,
    lang: String,
    meaningLang: String,
    collection: String = ""
): String? {
    if (lang.isEmpty() || meaningLang.isEmpty()) return null
    val wanted = collection.trim().lowercase()
    if (wanted.isNotEmpty()) {
        val scoped = index.collectionPairs.firstOrNull {
            it.collection.equals(wanted, ignoreCase = true) &&
                it.lang == lang &&
                it.meaningLang == meaningLang
        }
        if (scoped != null) return if (scoped.tier == TIER_FULL) TIER_FULL else TIER_THIN
        if (wanted == COLLECTION_EVERYDAY && index.collectionPairs.isEmpty()) {
            val legacy = index.pairs.firstOrNull { it.lang == lang && it.meaningLang == meaningLang }
            if (legacy != null) return if (legacy.tier == TIER_FULL) TIER_FULL else TIER_THIN
        }
        val has = index.decks.any {
            matchesCollection(it, wanted) && it.lang == lang && it.meaningLang == meaningLang
        }
        return if (has) TIER_THIN else null
    }
    val pair = index.pairs.firstOrNull { it.lang == lang && it.meaningLang == meaningLang }
    if (pair != null) return if (pair.tier == TIER_FULL) TIER_FULL else TIER_THIN
    // An index whose pipeline predates the pair table still has decks, and a
    // pair that has decks is not absent.
    val has = index.decks.any { it.lang == lang && it.meaningLang == meaningLang }
    return if (has) TIER_THIN else null
}

/**
 * The name the deck is stored under.
 *
 * It has to be stable, so downloading the same deck twice replaces it instead of
 * making a second copy, and it has to be distinct from the identifiers a user's
 * own imported file gets, so a catalogue deck can never quietly overwrite
 * something somebody typed in themselves. Hence the prefix, and hence the id
 * being scrubbed down to the characters an identifier is made of.
 */
fun catalogPackId(deckId: String): String {
    val slug = deckId.lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
    return if (slug.isEmpty()) "catalog-deck" else "catalog-$slug"
}

/**
 * The file name the deck is imported under.
 *
 * The import path already exists -- it is the one a shared deck file goes
 * through -- and it takes a file name, so the name is built to look like the file
 * this deck would have been if somebody had been sent it.
 */
fun catalogFileName(deck: CatalogDeck): String = catalogPackId(deck.id) + ".jsonl"

/**
 * Whether the licence obliges the deck to name somebody.
 *
 * CC0 does not, and saying "by" over a public-domain dedication would be putting
 * words in the dedicator's mouth. Everything else here does, and the line is
 * shown before the download rather than after it.
 */
fun licenceNeedsAttribution(licence: String): Boolean {
    val plain = licence.trim().lowercase()
    if (plain.isEmpty()) return false
    return !plain.startsWith("cc0") && !plain.contains("public domain")
}

/** Whether the licence binds a deck built out of this one to the same terms. */
fun licenceIsShareAlike(licence: String): Boolean =
    licence.trim().lowercase().let { it.contains("by-sa") || it.contains("sharealike") }

/**
 * The size line, in megabytes with one decimal.
 *
 * Not the updater's function, deliberately: that one is about a file on disk and
 * this one about a number out of an index that may be missing, and a missing size
 * has to read as "unknown" rather than as "0.0".
 */
fun catalogSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
    if (megabytes < 0.1) return "0.1"
    // Fixed locale, because this is a number on a download button and not prose:
    // a phone set to Russian would otherwise render it "1,4" here and "1.4" in
    // the updater, which are the same file and have to read the same way.
    return String.format(java.util.Locale.US, "%.1f", megabytes)
}

val LEVEL_ORDER: List<String> = listOf("beginner", "middle", "advanced")
