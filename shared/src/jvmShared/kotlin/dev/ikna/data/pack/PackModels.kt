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
    val isContent: Boolean,
    /** Catalogue v2: Universal Dependencies POS when morphology resolves it. */
    val upos: String? = null,
    /** Catalogue v2: canonical CoNLL-U FEATS string. */
    val feats: String? = null,
    /** Catalogue v2: where the emitted lemma/morphology decision came from. */
    val lemmaSource: String? = null
)

@Serializable
data class PackContext(
    val context: String,
    val translation: String,
    val targetStart: Int,
    val targetEnd: Int,
    val freqRank: Int,
    val tokens: List<PackToken>,
    val contextId: String,
    val meaningId: String,
    val sourceFamily: String,
    val ipaContext: String? = null
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
    val audioRef: String? = null,
    /**
     * IPA for [text], written by the catalogue pipeline.
     *
     * Defaulted, not required. Every deck published before transcriptions
     * existed lacks the key, every deck a person writes by hand lacks it too,
     * and a reader that refused those files would have turned a new field into
     * a broken catalogue.
     */
    val ipa: String? = null,
    /** IPA for [context]. Absent whenever [ipa] is. */
    val ipaContext: String? = null,
    /** Catalogue v2 learning-target identity. Old and hand-made packs omit it. */
    val targetId: String? = null,
    /** Catalogue v2 stable source reference for [context]. */
    val contextId: String? = null,
    /** Catalogue v2 stable source reference for the meaning/aligned segment. */
    val meaningId: String? = null,
    /** Catalogue v2 source-family id from the catalogue index registry. */
    val sourceFamily: String? = null,
    /** Additional natural contexts for the same target. The primary context stays
     * in the legacy fields above so pre-v2 readers still get one usable card. */
    val contexts: List<PackContext> = emptyList()
)
