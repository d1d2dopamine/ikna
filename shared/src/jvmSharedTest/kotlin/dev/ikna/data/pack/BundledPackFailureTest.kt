package dev.ikna.data.pack

import dev.ikna.data.db.ChunkDao
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.db.PackEntity
import dev.ikna.data.repo.prepareFirstSession
import dev.ikna.platform.Assets
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Actual loader, synthetic in-memory assets and a deliberately narrow DAO double. */
class BundledPackFailureTest {
    private val manifest = """{"packs":[{"id":"starter","version":1,"lang":"en","sourceLang":"en","title":"Fixture","chunkCount":1,"file":"fixture.jsonl"}]}"""
    private val row = """{"id":"fixture","text":"test","context":"A test.","translation":"test","targetStart":2,"targetEnd":6,"freqRank":1,"tokens":[]}"""

    private class Store {
        val chunks = linkedMapOf<String, ChunkEntity>()
        val packs = linkedMapOf<String, PackEntity>()
        var writeFailure: RuntimeException? = null
        val dao = Proxy.newProxyInstance(
            ChunkDao::class.java.classLoader, arrayOf(ChunkDao::class.java)
        ) { _, method, arguments ->
            val args = arguments.orEmpty()
            when (method.name) {
                "pack" -> packs[args[0] as String]
                "upsertChunks" -> {
                    writeFailure?.let { throw it }
                    @Suppress("UNCHECKED_CAST")
                    (args[0] as List<ChunkEntity>).forEach { chunks[it.id] = it }
                    Unit
                }
                "upsertPack" -> {
                    val pack = args[0] as PackEntity
                    packs[pack.id] = pack
                    Unit
                }
                "upsertPackChunks", "upsertChunkContexts" -> Unit
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as ChunkDao
    }

    private fun assets(deck: () -> java.io.InputStream): Assets = Assets { path ->
        if (path == "packs/manifest.json") manifest.byteInputStream() else deck()
    }

    @Test
    fun databaseFailureCannotCompleteOnboardingAndRetryIsIdempotent() = runBlocking {
        val store = Store()
        val failure = IllegalStateException("disposable database write failed")
        store.writeFailure = failure
        val loader = PackLoader(assets { row.byteInputStream() }, store.dao)
        var plans = 0
        var completions = 0
        suspend fun start() = prepareFirstSession(
            { loader.installBundledPacks() }, { plans++ }, { completions++ }
        )
        assertSame(failure, assertFailsWith<IllegalStateException> { start() })
        assertEquals(0, plans)
        assertEquals(0, completions)
        assertTrue(store.packs.isEmpty())
        store.writeFailure = null
        start()
        loader.installBundledPacks()
        assertEquals(setOf("fixture"), store.chunks.keys)
        assertEquals(setOf("starter"), store.packs.keys)
        assertEquals(1, plans)
        assertEquals(1, completions)
    }

    @Test
    fun optionalMissingDeckRemainsNonfatal() = runBlocking {
        val store = Store()
        val calls = mutableListOf<String>()
        prepareFirstSession(
            installPacks = {
                PackLoader(assets { throw FileNotFoundException("optional fixture") }, store.dao)
                    .installBundledPacks()
                calls += "optional install"
            },
            preparePlan = { calls += "empty plan" },
            markComplete = { calls += "complete" }
        )
        assertEquals(listOf("optional install", "empty plan", "complete"), calls)
        assertTrue(store.packs.isEmpty())
        assertTrue(store.chunks.isEmpty())
    }

    @Test
    fun ordinaryAssetReadFailureIsNotMissingContent() = runBlocking {
        val store = Store()
        val failure = IOException("disposable asset unreadable")
        assertSame(failure, assertFailsWith<IOException> {
            PackLoader(assets { throw failure }, store.dao).installBundledPacks()
        })
        assertTrue(store.packs.isEmpty())
    }

    @Test
    fun malformedContentDoesNotMarkPackInstalled() = runBlocking {
        val store = Store()
        val result = runCatching {
            PackLoader(assets { "broken JSON".byteInputStream() }, store.dao).installBundledPacks()
        }
        assertTrue(result.isFailure)
        assertTrue(store.packs.isEmpty())
    }

    @Test
    fun cancellationPropagatesFromDatabaseInsteadOfCompleting() = runBlocking {
        val store = Store()
        val cancellation = CancellationException("fixture cancelled")
        store.writeFailure = cancellation
        var completed = false
        assertSame(cancellation, assertFailsWith<CancellationException> {
            prepareFirstSession(
                { PackLoader(assets { row.byteInputStream() }, store.dao).installBundledPacks() },
                {}, { completed = true }
            )
        })
        assertFalse(completed)
        assertTrue(store.packs.isEmpty())
    }
}
