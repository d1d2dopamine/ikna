package dev.ikna.sync.remote

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException

/** All values come from trusted local state or an already authenticated parent object. */
internal data class RemoteEnvelopeContext(
    val group: String,
    val historyEpoch: String,
    val profile: String,
    val objectKind: String,
    val objectId: String,
    val publisher: String,
    val keyGeneration: Long,
    val revision: Long,
) {
    init {
        listOf(group, historyEpoch, objectKind, objectId, publisher).forEach {
            require(it.matches(Regex("[a-zA-Z0-9_-]{1,128}")))
        }
        require(profile == "production" || profile == "developer")
        require(keyGeneration > 0 && revision > 0)
    }

    internal fun authenticatedData(): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            out.writeInt(1)
            listOf("ikna-remote-envelope", group, historyEpoch, profile, objectKind, objectId, publisher)
                .forEach { value ->
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
            out.writeLong(keyGeneration)
            out.writeLong(revision)
        }
    }.toByteArray()
}

/**
 * Bounded opaque bytes using Tink's nonce/ciphertext handling, not a new cipher.
 * Does not establish freshness, parse journals, persist keys or activate networking.
 * The caller must durably retain upload ciphertext and use authenticated checkpoints.
 */
internal class EncryptedRemoteEnvelope private constructor(private val aead: Aead) {
    fun encrypt(context: RemoteEnvelopeContext, plaintext: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "remote-envelope-too-large" }
        val ciphertext = aead.encrypt(plaintext, context.authenticatedData())
        require(ciphertext.size <= MAX_CIPHERTEXT_BYTES) { "remote-envelope-too-large" }
        return ByteBuffer.allocate(HEADER_BYTES + ciphertext.size)
            .putInt(MAGIC).putInt(VERSION).putInt(ciphertext.size).put(ciphertext).array()
    }

    fun decrypt(context: RemoteEnvelopeContext, envelope: ByteArray): ByteArray {
        // Bound before allocation and before invoking the cryptographic library.
        require(envelope.size in HEADER_BYTES..MAX_ENVELOPE_BYTES) { "invalid-remote-envelope" }
        val buffer = ByteBuffer.wrap(envelope)
        require(buffer.int == MAGIC && buffer.int == VERSION) { "invalid-remote-envelope" }
        val size = buffer.int
        require(size in 40..MAX_CIPHERTEXT_BYTES && size == buffer.remaining()) { "invalid-remote-envelope" }
        val ciphertext = ByteArray(size).also(buffer::get)
        val plaintext = try {
            aead.decrypt(ciphertext, context.authenticatedData())
        } catch (_: GeneralSecurityException) {
            // Never retain a library/provider error message that could expose sensitive context.
            throw GeneralSecurityException("remote-envelope-authentication-failed")
        }
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "invalid-remote-envelope" }
        return plaintext
    }

    companion object {
        const val MAX_PLAINTEXT_BYTES = 4 * 1024 * 1024
        private const val MAX_CIPHERTEXT_BYTES = MAX_PLAINTEXT_BYTES + 45
        private const val HEADER_BYTES = 12
        const val MAX_ENVELOPE_BYTES = HEADER_BYTES + MAX_CIPHERTEXT_BYTES
        private const val MAGIC = 0x494B5245 // IKRE
        private const val VERSION = 1
        private const val KEY_TYPE = "type.googleapis.com/google.crypto.tink.XChaCha20Poly1305Key"

        /** Provisioning/unwrapping belongs to a future platform key-store owner. */
        fun fromKeyset(keyset: KeysetHandle): EncryptedRemoteEnvelope {
            AeadConfig.register()
            val info = keyset.keysetInfo
            require(info.keyInfoCount in 1..8 && info.keyInfoList.all { it.typeUrl == KEY_TYPE }) {
                "unsupported-remote-keyset"
            }
            return EncryptedRemoteEnvelope(keyset.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
        }
    }
}
