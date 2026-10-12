package dev.ikna.desktop

import dev.ikna.data.catalog.importCatalogDeck
import dev.ikna.data.db.ChunkContextEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.ChunkTokenEntity
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.PackChunkEntity
import dev.ikna.data.db.PackEntity
import dev.ikna.data.db.openIknaDatabase
import dev.ikna.data.pack.PackChunk
import dev.ikna.data.pack.PackLoader
import dev.ikna.data.pack.PackToken
import dev.ikna.platform.Assets
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Uses only disposable databases and explicitly synthetic DEV fixtures; no network or user data. */
class CatalogImportIntegrationTest {
    private enum class Stage { CHUNKS, TOKENS, MEMBERSHIPS, CONTEXTS, METADATA }

    @Test
    fun `failure after each write stage rolls back content and metadata`() = runBlocking {
        for (stage in Stage.entries) withDatabase { db ->
            val dao = db.chunkDao()
            val failingLoader = loader(interceptWrites(dao) { reached ->
                if (reached == stage) throw IllegalStateException("injected $stage")
            })
            try {
                importCatalogDeck(db, failingLoader, PACK, "DEV test", "en", fixture())
                fail("Expected failure after $stage")
            } catch (failed: IllegalStateException) {
                assertEquals("injected $stage", failed.message)
            }
            assertAbsent(dao)
            // The rolled-back connection remains usable and retry installs exactly once.
            importCatalogDeck(db, loader(dao), PACK, "DEV test", "en", fixture())
            assertInstalled(dao)
        }
    }

    @Test
    fun `screen coroutine cancellation after each write stage rolls back`() = runBlocking {
        for (stage in Stage.entries) withDatabase { db ->
            val reachedStage = CompletableDeferred<Unit>()
            val releaseStage = CompletableDeferred<Unit>()
            val dao = db.chunkDao()
            val loader = loader(interceptWrites(dao) { reached ->
                if (reached == stage) {
                    reachedStage.complete(Unit)
                    releaseStage.await()
                }
            })
            val install = launch {
                importCatalogDeck(db, loader, PACK, "DEV test", "en", fixture())
                fail("Cancelled import returned success")
            }
            try {
                withTimeout(10_000) { reachedStage.await() }
                install.cancel()
                releaseStage.complete(Unit)
                withTimeout(10_000) { install.join() }
            } finally {
                releaseStage.complete(Unit)
                install.cancelAndJoin()
            }
            assertTrue(install.isCancelled)
            assertAbsent(dao)
        }
    }

    @Test
    fun `reimport keeps one target and membership and preserves metadata choices`() = runBlocking {
        withDatabase { db ->
            val dao = db.chunkDao()
            val loader = loader(dao)
            val first = importCatalogDeck(db, loader, PACK, "DEV test", "en", fixture())
            val initial = requireNotNull(dao.pack(PACK))
            dao.setPackActive(PACK, false)
            val again = importCatalogDeck(db, loader, PACK, "DEV renamed", "en", fixture())
            assertEquals(first, again)
            assertInstalled(dao)
            val updated = requireNotNull(dao.pack(PACK))
            assertEquals(initial.installedAt, updated.installedAt)
            assertEquals(false, updated.isActive)
            assertEquals(initial.version + 1, updated.version)
            assertEquals("DEV renamed", updated.title)
        }
    }

    @Test
    fun `failed reimport restores previously installed content and metadata`() = runBlocking {
        withDatabase { db ->
            val dao = db.chunkDao()
            importCatalogDeck(db, loader(dao), PACK, "DEV original", "en", fixture())
            val originalPack = dao.pack(PACK)
            val originalContexts = dao.contextsFor(TARGET)
            val originalMemberships = dao.packChunks(PACK)
            val failing = loader(interceptWrites(dao) {
                if (it == Stage.METADATA) error("injected replacement failure")
            })
            try {
                importCatalogDeck(db, failing, PACK, "DEV replacement", "en", fixture("Changed DEV meaning"))
                fail("Expected replacement failure")
            } catch (failed: IllegalStateException) {
                assertEquals("injected replacement failure", failed.message)
            }
            assertEquals(originalPack, dao.pack(PACK))
            assertEquals(originalContexts, dao.contextsFor(TARGET))
            assertEquals(originalMemberships, dao.packChunks(PACK))
            assertInstalled(dao)
        }
    }

    @Test
    fun `empty or malformed catalogue import does not create or replace metadata`() = runBlocking {
        for (text in listOf("", "not json")) withDatabase { db ->
            val dao = db.chunkDao()
            for (alreadyInstalled in listOf(false, true)) {
                if (alreadyInstalled) importCatalogDeck(db, loader(dao), PACK, "DEV original", "en", fixture())
                val before = dao.pack(PACK)
                try {
                    importCatalogDeck(db, loader(dao), PACK, "DEV rejected", "en", text)
                    fail("Expected empty import rejection")
                } catch (_: IllegalArgumentException) {
                    assertEquals(before, dao.pack(PACK))
                }
                if (alreadyInstalled) assertInstalled(dao) else assertAbsent(dao)
            }
        }
    }

    private fun interceptWrites(dao: ChunkDao, after: suspend (Stage) -> Unit): ChunkDao =
        object : ChunkDao by dao {
            override suspend fun insertChunksIgnore(chunks: List<ChunkEntity>) {
                dao.insertChunksIgnore(chunks)
                after(Stage.CHUNKS)
            }
            override suspend fun insertTokensIgnore(tokens: List<ChunkTokenEntity>) {
                dao.insertTokensIgnore(tokens)
                after(Stage.TOKENS)
            }
            override suspend fun upsertPackChunks(rows: List<PackChunkEntity>) {
                dao.upsertPackChunks(rows)
                after(Stage.MEMBERSHIPS)
            }
            override suspend fun upsertChunkContexts(rows: List<ChunkContextEntity>) {
                dao.upsertChunkContexts(rows)
                after(Stage.CONTEXTS)
            }
            override suspend fun upsertPack(pack: PackEntity) {
                dao.upsertPack(pack)
                after(Stage.METADATA)
            }
        }

    private fun loader(dao: ChunkDao) = PackLoader(Assets { error("No bundled assets in this test") }, dao)

    private suspend fun assertAbsent(dao: ChunkDao) {
        assertNull(dao.pack(PACK))
        assertNull(dao.chunk(TARGET))
        assertTrue(dao.tokensFor(listOf(TARGET)).isEmpty())
        assertTrue(dao.packChunks(PACK).isEmpty())
        assertTrue(dao.contextsFor(TARGET).isEmpty())
    }

    private suspend fun assertInstalled(dao: ChunkDao) {
        assertEquals(1, dao.packs().size)
        assertEquals(1, dao.pack(PACK)?.chunkCount)
        assertNotNull(dao.chunk(TARGET))
        assertEquals(1, dao.chunks(listOf(TARGET)).size)
        assertEquals(1, dao.tokensFor(listOf(TARGET)).size)
        assertEquals(1, dao.packChunks(PACK).size)
        assertEquals(1, dao.contextsFor(TARGET).size)
    }

    private suspend fun withDatabase(block: suspend (IknaDatabase) -> Unit) {
        val home = Files.createTempDirectory("ikna-dev-catalog-atomic-").toFile()
        val db = openIknaDatabase(home.resolve("disposable-dev.db"))
        try {
            block(db)
        } finally {
            db.close()
            home.deleteRecursively()
        }
    }

    private fun fixture(meaning: String = "DEV test meaning") = Json.encodeToString(
        PackChunk(
            id = "dev-catalog-row", text = "sample", context = "sample", translation = meaning,
            targetStart = 0, targetEnd = 6, freqRank = 1,
            tokens = listOf(PackToken("sample", "sample", "NOUN", true)),
            targetId = TARGET, contextId = "dev-source", meaningId = "dev-meaning", sourceFamily = "developer"
        )
    )

    private companion object {
        const val PACK = "dev-catalog-pack"
        const val TARGET = "dev-catalog-target"
    }
}
