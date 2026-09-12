package dev.ikna.data.catalog

import dev.ikna.data.pack.PackChunk
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogV2ModelTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `v1 index still parses with v2 model defaults`() {
        val index = json.decodeFromString<CatalogIndex>(
            """{"version":1,"builtAt":"2026-09-12","decks":[],"pairs":[]}"""
        )
        assertEquals(1, index.version)
        assertEquals(1, index.catalogueVersion)
        assertEquals(emptyList<CatalogCollection>(), index.collections)
        assertNull(index.targetIdentity)
        assertNull(index.morphology)
    }

    @Test
    fun `v2 index exposes collection and provenance registries`() {
        val index = json.decodeFromString<CatalogIndex>(
            """{
              "version":2,
              "catalogueVersion":2,
              "builtAt":"2026-09-12",
              "targetIdentity":{"version":2,"method":"nfkc-casefold-exact"},
              "collections":[{"id":"everyday","title":"Everyday","description":"General language"}],
              "sourceFamilies":[{"id":"tatoeba","title":"Tatoeba","homepage":"https://tatoeba.org","licence":"CC BY 2.0 FR","attribution":"Tatoeba contributors"}],
              "morphology":{"ruleVersion":1,"policy":"ud-exact-context-then-unanimous-form","targetIdentityAffected":false,"datasets":[{"id":"unimorph-eng","sourceFamily":"unimorph","kind":"unimorph","lang":"en","sourceVersion":"pinned","sourceUrl":"https://github.com/unimorph/eng","licence":"CC BY-SA 3.0","licenceUrl":"https://creativecommons.org/licenses/by-sa/3.0/","attribution":"UniMorph English"}]},
              "decks":[{"id":"en-ru-everyday-beginner","title":"English from Russian - Everyday - beginner","lang":"en","meaningLang":"ru","collection":"everyday","sourceFamily":"tatoeba","morphologySources":["unimorph-eng"]}],
              "pairs":[],
              "collectionPairs":[]
            }"""
        )
        assertEquals(2, index.version)
        assertEquals(2, index.catalogueVersion)
        assertEquals("everyday", index.collections.single().id)
        assertEquals("tatoeba", index.sourceFamilies.single().id)
        assertEquals("everyday", index.decks.single().collection)
        assertEquals(listOf("unimorph-eng"), index.decks.single().morphologySources)
        assertEquals("nfkc-casefold-exact", index.targetIdentity?.method)
        assertEquals(1, index.morphology?.ruleVersion)
        assertEquals("unimorph-eng", index.morphology?.datasets?.single()?.id)
        assertEquals(false, index.morphology?.targetIdentityAffected)
    }

    @Test
    fun `v2 card metadata is optional for old packs and readable when present`() {
        val oldCard = json.decodeFromString<PackChunk>(
            """{"id":"old-1","text":"care","context":"I care.","translation":"Мне не все равно.","targetStart":2,"targetEnd":6,"freqRank":10,"tokens":[]}"""
        )
        assertNull(oldCard.targetId)

        val v2Card = json.decodeFromString<PackChunk>(
            """{"id":"new-1","text":"care","context":"I care.","translation":"Мне не все равно.","targetStart":2,"targetEnd":6,"freqRank":10,"tokens":[{"surface":"care","lemma":"care","pos":"WORD","isContent":true,"upos":"VERB","feats":"VerbForm=Fin","lemmaSource":"unimorph"}],"targetId":"t2:en:accfde82ba2383f2","contextId":"tatoeba:1001","meaningId":"tatoeba:2001","sourceFamily":"tatoeba"}"""
        )
        assertEquals("t2:en:accfde82ba2383f2", v2Card.targetId)
        assertEquals("VERB", v2Card.tokens.single().upos)
        assertEquals("unimorph", v2Card.tokens.single().lemmaSource)
    }
}
