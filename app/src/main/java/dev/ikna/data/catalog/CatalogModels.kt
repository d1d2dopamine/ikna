package dev.ikna.data.catalog

import kotlinx.serialization.Serializable

/*
 * What the catalogue is, as data.
 *
 * A deck in the catalogue was not written by anybody here. It was cut out of two
 * open corpora by the pipeline in tools/catalog, and the licence it arrived
 * under travels with it -- in this index, on the screen before it is downloaded,
 * and inside the deck's own lines afterwards. docs/SOURCES.md is the contract
 * these types implement; if a field here stops matching that file, that file is
 * the one that is right.
 *
 * Everything is optional except the handful of things a row cannot be drawn
 * without, and unknown keys are ignored when the index is read, so a catalogue
 * built by a newer pipeline never makes an older app refuse to open it.
 */

/** One deck offered for download. */
@Serializable
data class CatalogDeck(
    val id: String,
    val title: String,
    /** The language being learned; also the language the voice reads. */
    val lang: String,
    /** The language the meanings are in. */
    val meaningLang: String,
    val chunkCount: Int = 0,
    /** File name inside the catalogue release. Never a URL. */
    val file: String = "",
    val sizeBytes: Long = 0L,
    /** Empty for general vocabulary; otherwise a topic, already translated. */
    val subject: String = "",
    /** "beginner", "middle", "advanced", or empty when the deck is not levelled. */
    val level: String = "",
    /** Spelled out, as it will be shown: "CC BY-SA 4.0", "CC0 1.0". */
    val licence: String = "",
    /** One line naming who is being credited, kept with the deck. */
    val attribution: String = "",
    /** The corpora this deck was cut out of, for the line under the licence. */
    val sources: List<String> = emptyList(),
    val version: Int = 1
)

/**
 * How well a language pair is served, computed by the pipeline from the data
 * rather than decided in the app.
 *
 * FULL and THIN both appear in the catalogue; a pair that is not in the
 * catalogue at all has no row here, which is the third state and needs no name.
 */
@Serializable
data class CatalogPair(
    val lang: String,
    val meaningLang: String,
    /** "full" or "thin". Anything else is treated as thin. */
    val tier: String = TIER_THIN,
    val deckCount: Int = 0,
    val chunkCount: Int = 0
)

@Serializable
data class CatalogIndex(
    val version: Int = 1,
    /** When the pipeline ran, ISO date. Shown so a stale catalogue is visible. */
    val builtAt: String = "",
    val decks: List<CatalogDeck> = emptyList(),
    val pairs: List<CatalogPair> = emptyList()
)

const val TIER_FULL: String = "full"
const val TIER_THIN: String = "thin"
