package dev.ikna.sync.remote

import java.util.UUID

/** Synthetic test backend only: process-local, no durability, credentials or network. */
internal class InMemoryRemoteBlobStore(
    private val maximumObjectBytes: Int = EncryptedRemoteEnvelope.MAX_ENVELOPE_BYTES,
    private val maximumTotalBytes: Long = 64L * 1024 * 1024,
) : RemoteBlobStore {
    private val lock = Any()
    private val objects = mutableMapOf<RemoteObjectKey, RemoteBlob>()
    private var totalBytes = 0L

    init { require(maximumObjectBytes > 0 && maximumTotalBytes >= maximumObjectBytes) }

    override suspend fun get(key: RemoteObjectKey, maximumBytes: Int): RemoteBlob? = synchronized(lock) {
        require(maximumBytes in 1..maximumObjectBytes)
        objects[key]?.let {
            val bytes = it.bytes()
            if (bytes.size > maximumBytes) throw RemoteStoreException(RemoteFailure.TOO_LARGE)
            RemoteBlob(bytes, it.version)
        }
    }

    override suspend fun create(key: RemoteObjectKey, bytes: ByteArray): RemoteVersion = synchronized(lock) {
        if (objects.containsKey(key)) throw RemoteStoreException(RemoteFailure.CONFLICT)
        write(key, bytes)
    }

    override suspend fun replace(key: RemoteObjectKey, expected: RemoteVersion, bytes: ByteArray): RemoteVersion =
        synchronized(lock) {
            if (objects[key]?.version != expected) throw RemoteStoreException(RemoteFailure.CONFLICT)
            write(key, bytes)
        }

    private fun write(key: RemoteObjectKey, bytes: ByteArray): RemoteVersion {
        if (bytes.size > maximumObjectBytes) throw RemoteStoreException(RemoteFailure.TOO_LARGE)
        val newTotal = totalBytes - (objects[key]?.bytes()?.size ?: 0) + bytes.size
        if (newTotal > maximumTotalBytes) throw RemoteStoreException(RemoteFailure.QUOTA)
        val version = RemoteVersion(UUID.randomUUID().toString())
        objects[key] = RemoteBlob(bytes, version)
        totalBytes = newTotal
        return version
    }
}
