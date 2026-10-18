package dev.ikna.sync.remote

import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RemoteBlobStoreTest {
    private val key = RemoteObjectKey("head")

    @Test fun absenceCreateConflictAndDefensiveCopies() = runBlocking {
        val store = InMemoryRemoteBlobStore()
        assertNull(store.get(key, 100))
        val bytes = byteArrayOf(1, 2, 3)
        val first = store.create(key, bytes)
        bytes[0] = 9
        assertEquals(RemoteFailure.CONFLICT, assertFailsWith<RemoteStoreException> {
            store.create(key, byteArrayOf(4))
        }.failure)
        val result = requireNotNull(store.get(key, 100))
        result.bytes()[0] = 8
        assertContentEquals(byteArrayOf(1, 2, 3), requireNotNull(store.get(key, 100)).bytes())
        val second = store.replace(key, first, byteArrayOf(4))
        assertNotEquals(first, second)
        assertEquals(RemoteFailure.CONFLICT, assertFailsWith<RemoteStoreException> {
            store.replace(key, first, byteArrayOf(5))
        }.failure)
    }

    @Test fun concurrentCasHasExactlyOneWinner() = runBlocking {
        val store = InMemoryRemoteBlobStore()
        val first = store.create(key, byteArrayOf(0))
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { value -> executor.submit<Boolean> {
                start.await()
                runBlocking {
                    try { store.replace(key, first, byteArrayOf(value.toByte())); true }
                    catch (error: RemoteStoreException) {
                        assertEquals(RemoteFailure.CONFLICT, error.failure)
                        false
                    }
                }
            } }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally { executor.shutdownNow() }
    }

    @Test fun boundsQuotaAndAbsentReplacementFailWithoutMutation() = runBlocking {
        val store = InMemoryRemoteBlobStore(maximumObjectBytes = 4, maximumTotalBytes = 6)
        assertEquals(RemoteFailure.CONFLICT, assertFailsWith<RemoteStoreException> {
            store.replace(key, RemoteVersion("missing"), byteArrayOf(1))
        }.failure)
        assertEquals(RemoteFailure.TOO_LARGE, assertFailsWith<RemoteStoreException> {
            store.create(key, ByteArray(5))
        }.failure)
        val first = store.create(key, ByteArray(4))
        assertEquals(RemoteFailure.TOO_LARGE, assertFailsWith<RemoteStoreException> { store.get(key, 3) }.failure)
        assertEquals(RemoteFailure.QUOTA, assertFailsWith<RemoteStoreException> {
            store.create(RemoteObjectKey("other"), ByteArray(3))
        }.failure)
        assertEquals(first, requireNotNull(store.get(key, 4)).version)
        assertNull(store.get(RemoteObjectKey("other"), 4))
    }

    @Test fun unsafeObjectKeysAreRejected() {
        listOf("", "../secret", "head/path", "https://example.com", "a b", "x".repeat(129)).forEach {
            assertFailsWith<IllegalArgumentException> { RemoteObjectKey(it) }
        }
    }
}
