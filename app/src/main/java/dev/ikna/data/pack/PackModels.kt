package dev.ikna.data.pack

import kotlinx.serialization.Serializable

@Serializable
data class PackManifest(
    val id: String,
    val version: Int,
    val lang: String,
    val sourceLang: String,
    val title: String,
    val chunkCount: Int,
    val file: String,
    /**
     * Whether the deck is switched on the first time it is installed. A
     * second language shipped as "on" would interleave two languages inside
     * one session, so extra packs arrive off and wait in the decks screen.
     */
    val active: Boolean = true
)

@Serializable
data class PackIndex(val packs: List<PackManifest>)

@Serializable
data class PackToken(
    val surface: String,
    val lemma: String,
    val pos: String,
    val isContent: Boolean
)

@Serializable
data class PackChunk(
    val id: String,
    val text: String,
    val context: String,
    val translation: String,
    val targetStart: Int,
    val targetEnd: Int,
    val freqRank: Int,
    val tokens: List<PackToken>,
    val audioRef: String? = null
)
