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
    val file: String
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
