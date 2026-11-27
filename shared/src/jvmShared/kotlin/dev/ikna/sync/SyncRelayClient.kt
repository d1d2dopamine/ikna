package dev.ikna.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encrypted, zero-storage HTTPS rendezvous client for paired sync across VPNs and
 * separate networks.
 *
 * Security boundaries:
 * - Every frame is authenticated and encrypted locally with AES-256-GCM (fresh 96-bit CSPRNG nonce
 *   per frame, AAD bound to opaque channel ID and sender slot) BEFORE leaving the device.
 * - The relay server only ever sees an opaque 64-hex channel ID, a 16-hex slot tag, and opaque
 *   ciphertext chunks kept in volatile RAM for <= 30 seconds and deleted immediately upon pickup.
 * - No deck text, review history, certificates, device names, or credentials are exposed to the relay.
 * - Plaintext http:// is rejected for all non-loopback hosts; redirects are never followed.
 */
object SyncRelayClient {
    private const val PROTOCOL_VERSION = 1
    private const val NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val MAX_CHUNK_RAW_BYTES = 512 * 1024
    private const val MAX_CHUNKS_PER_MESSAGE = 128
    private const val MAX_HTTP_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_EXTRA_MS = 8_000
    private const val MAX_URL_LENGTH = 256

    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true; isLenient = false; encodeDefaults = true }

    @Serializable
    internal data class ChunkEnvelope(
        val v: Int = PROTOCOL_VERSION,
        val m: String,
        val i: Int,
        val n: Int,
        val f: String,
        val d: String,
    )

    /**
     * Validates and normalizes a relay base URL.
     * Returns empty string if [raw] is blank.
     * Throws [IllegalArgumentException] if the URL is malformed or uses plaintext http:// for a remote host.
     */
    fun normalizeRelayUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        require(trimmed.length <= MAX_URL_LENGTH) { "invalid-relay-url" }
        require(!trimmed.any { it.isWhitespace() || it.code < 0x20 }) { "invalid-relay-url" }
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            throw IllegalArgumentException("invalid-relay-url")
        }
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotEmpty() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "invalid-relay-url"
        }
        val loopback = host == "127.0.0.1" || host == "localhost"
        require(scheme == "https" || (scheme == "http" && loopback)) { "invalid-relay-url" }
        val portPart = if (uri.port > 0) ":${uri.port}" else ""
        val pathPart = uri.rawPath.orEmpty().trimEnd('/')
        return "$scheme://$host$portPart$pathPart"
    }

    /** Derives an opaque, unlinkable 64-hex channel identifier from [purpose] and [secret]. */
    fun channelId(purpose: String, secret: String): String =
        sha256Hex("ikna-relay-chan-v1|$purpose|$secret")

    /** Derives an opaque 16-hex mailbox slot identifier for [deviceId] within [channelId]. */
    fun slotId(channelId: String, deviceId: String): String =
        sha256Hex("ikna-relay-slot-v1|$channelId|$deviceId").take(16)

    /** Derives a 256-bit AES-GCM key from [purpose] and [secret]. */
    fun deriveKey(purpose: String, secret: String): ByteArray =
        sha256Bytes("ikna-relay-key-v1|$purpose|$secret")

    /** Encrypts [plaintext] using AES-256-GCM with a fresh random 96-bit nonce and bound AAD. */
    fun encrypt(key: ByteArray, channelId: String, senderSlot: String, plaintext: ByteArray): ByteArray {
        require(key.size == 32) { "invalid-relay-key" }
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(channelId, senderSlot))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(NONCE_BYTES + ciphertext.size).also { out ->
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES)
            System.arraycopy(ciphertext, 0, out, NONCE_BYTES, ciphertext.size)
        }
    }

    /** Decrypts and authenticates [sealed] using AES-256-GCM; rejects tampering or direction replay. */
    fun decrypt(key: ByteArray, channelId: String, expectedSenderSlot: String, sealed: ByteArray): ByteArray {
        require(key.size == 32) { "invalid-relay-key" }
        if (sealed.size < NONCE_BYTES + 16) throw GeneralSecurityException("relay-ciphertext-truncated")
        val nonce = sealed.copyOfRange(0, NONCE_BYTES)
        val ciphertext = sealed.copyOfRange(NONCE_BYTES, sealed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(channelId, expectedSenderSlot))
        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts and sends [plaintext] to [recipientSlot] on [channelId] via [relayBaseUrl],
     * automatically splitting large payloads into bounded 512 KiB chunks.
     */
    fun sendMessage(
        relayBaseUrl: String,
        channelId: String,
        senderSlot: String,
        recipientSlot: String,
        key: ByteArray,
        plaintext: ByteArray,
    ) {
        val base = normalizeRelayUrl(relayBaseUrl)
        require(base.isNotEmpty()) { "invalid-relay-url" }
        val sealed = encrypt(key, channelId, senderSlot, plaintext)
        val totalChunks = ((sealed.size + MAX_CHUNK_RAW_BYTES - 1) / MAX_CHUNK_RAW_BYTES).coerceAtLeast(1)
        require(totalChunks <= MAX_CHUNKS_PER_MESSAGE) { "relay-payload-too-large" }
        val msgId = UUID.randomUUID().toString().replace("-", "").take(16)
        val endpoint = "$base/v1/room/$channelId/send?to=$recipientSlot"
        for (index in 0 until totalChunks) {
            val start = index * MAX_CHUNK_RAW_BYTES
            val end = minOf(sealed.size, start + MAX_CHUNK_RAW_BYTES)
            val slice = sealed.copyOfRange(start, end)
            val envelope = ChunkEnvelope(
                m = msgId,
                i = index,
                n = totalChunks,
                f = senderSlot,
                d = Base64.getEncoder().encodeToString(slice),
            )
            val body = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
            postJson(endpoint, body)
        }
    }

    /**
     * Long-polls [mySlot] on [channelId] until one complete encrypted message from
     * [expectedSenderSlot] is reassembled and authenticated, or returns null on timeout.
     */
    fun receiveMessage(
        relayBaseUrl: String,
        channelId: String,
        mySlot: String,
        expectedSenderSlot: String,
        key: ByteArray,
        timeoutMs: Int,
        maxPlaintextBytes: Int,
        isActive: () -> Boolean = { true },
    ): ByteArray? {
        val base = normalizeRelayUrl(relayBaseUrl)
        require(base.isNotEmpty()) { "invalid-relay-url" }
        val deadlineNanos = System.nanoTime() + timeoutMs.toLong() * 1_000_000L
        val pending = LinkedHashMap<String, Array<ByteArray?>>()
        val pendingTotals = HashMap<String, Int>()
        while (isActive()) {
            val remainingMs = ((deadlineNanos - System.nanoTime()) / 1_000_000L).toInt()
            if (remainingMs <= 0) return null
            val waitMs = remainingMs.coerceIn(100, 4_000)
            val endpoint = "$base/v1/room/$channelId/poll?slot=$mySlot&wait=$waitMs"
            val chunks = pollChunks(endpoint, waitMs)
            for (chunk in chunks) {
                if (chunk.v != PROTOCOL_VERSION || chunk.f != expectedSenderSlot) continue
                if (chunk.n !in 1..MAX_CHUNKS_PER_MESSAGE || chunk.i !in 0 until chunk.n) continue
                val bytes = runCatching { Base64.getDecoder().decode(chunk.d) }.getOrNull() ?: continue
                if (bytes.size > MAX_CHUNK_RAW_BYTES + 64) continue
                val arr = pending.getOrPut(chunk.m) { arrayOfNulls(chunk.n) }
                if (arr.size != chunk.n) continue
                if (arr[chunk.i] == null) {
                    val nextTotal = (pendingTotals[chunk.m] ?: 0) + bytes.size
                    if (nextTotal > maxPlaintextBytes + 256) {
                        pending.remove(chunk.m)
                        pendingTotals.remove(chunk.m)
                        throw IllegalArgumentException("relay-payload-too-large")
                    }
                    arr[chunk.i] = bytes
                    pendingTotals[chunk.m] = nextTotal
                }
                if (arr.all { it != null }) {
                    pending.remove(chunk.m)
                    val totalSize = pendingTotals.remove(chunk.m) ?: 0
                    val combined = ByteArray(totalSize)
                    var offset = 0
                    for (part in arr) {
                        val p = part!!
                        System.arraycopy(p, 0, combined, offset, p.size)
                        offset += p.size
                    }
                    val plaintext = decrypt(key, channelId, expectedSenderSlot, combined)
                    if (plaintext.size > maxPlaintextBytes) {
                        throw IllegalArgumentException("relay-payload-too-large")
                    }
                    return plaintext
                }
            }
            if (chunks.isEmpty() && pending.isEmpty() && remainingMs <= waitMs) {
                return null
            }
        }
        return null
    }

    private fun postJson(endpoint: String, body: ByteArray) {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = CONNECT_TIMEOUT_MS + READ_TIMEOUT_EXTRA_MS
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            conn.outputStream.use { it.write(body) }
            val status = conn.responseCode
            if (status !in 200..299) {
                throw java.io.IOException("relay-http-$status")
            }
            runCatching { conn.inputStream?.close() }
        } finally {
            conn.disconnect()
        }
    }

    private fun pollChunks(endpoint: String, waitMs: Int): List<ChunkEnvelope> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = waitMs + READ_TIMEOUT_EXTRA_MS
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val status = conn.responseCode
            if (status == 204) return emptyList()
            if (status !in 200..299) throw java.io.IOException("relay-http-$status")
            val body = conn.inputStream.use { readBounded(it, MAX_HTTP_RESPONSE_BYTES) }
            if (body.isEmpty()) emptyList()
            else json.decodeFromString<List<ChunkEnvelope>>(body.toString(Charsets.UTF_8))
        } finally {
            conn.disconnect()
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw java.io.IOException("relay-response-too-large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun aad(channelId: String, senderSlot: String): ByteArray =
        "ikna-relay-aad-v1|$channelId|$senderSlot".toByteArray(Charsets.UTF_8)

    private fun sha256Bytes(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(input: String): String =
        sha256Bytes(input).joinToString("") { "%02x".format(it.toInt() and 255) }
}
