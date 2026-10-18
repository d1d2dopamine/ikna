package dev.ikna.sync.remote

/** Capability boundary only. No real provider or app setup is enabled. */
internal interface RemoteBlobStore {
    /** Complete bounded object, or null only for authoritative absence. */
    suspend fun get(key: RemoteObjectKey, maximumBytes: Int): RemoteBlob?
    /** Atomic create-if-absent; never silently replaces an existing object. */
    suspend fun create(key: RemoteObjectKey, bytes: ByteArray): RemoteVersion
    /** Atomic compare-and-swap. An absent object also conflicts. */
    suspend fun replace(key: RemoteObjectKey, expected: RemoteVersion, bytes: ByteArray): RemoteVersion
}

/** Opaque identifiers only, no card text, credentials, relative paths or URLs. */
internal data class RemoteObjectKey(val value: String) {
    init { require(value.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) }
}

/** Provider version token, not an authenticity proof. Do not log provider tokens. */
internal class RemoteVersion(val value: String) {
    init { require(value.isNotEmpty() && value.length <= 1024) }
    override fun equals(other: Any?): Boolean = other is RemoteVersion && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "RemoteVersion(<redacted>)"
}

internal class RemoteBlob(bytes: ByteArray, val version: RemoteVersion) {
    private val content = bytes.copyOf()
    fun bytes(): ByteArray = content.copyOf()
    override fun toString(): String = "RemoteBlob(size=${content.size}, version=<redacted>)"
}

internal enum class RemoteFailure {
    CONFLICT, TOO_LARGE, UNAVAILABLE, UNAUTHORIZED, QUOTA, UNSUPPORTED, INDETERMINATE_WRITE,
}

/** Deliberately does not retain provider response bodies or credential-bearing URLs. */
internal class RemoteStoreException(val failure: RemoteFailure) : Exception("remote-${failure.name.lowercase()}")
