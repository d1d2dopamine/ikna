package dev.ikna.sync.remote

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class EncryptedRemoteEnvelopeTest {
    private val context = RemoteEnvelopeContext("group", "epoch", "production", "journal", "object", "pc", 1, 1)
    private fun codec(): EncryptedRemoteEnvelope {
        AeadConfig.register()
        return EncryptedRemoteEnvelope.fromKeyset(KeysetHandle.generateNew(PredefinedAeadParameters.XCHACHA20_POLY1305))
    }

    @Test fun roundTripUsesRandomizedCiphertextAndRejectsWrongKey() {
        val codec = codec()
        val plaintext = "synthetic-journal".toByteArray()
        val first = codec.encrypt(context, plaintext)
        val second = codec.encrypt(context, plaintext)
        assertFalse(first.contentEquals(second))
        assertContentEquals(plaintext, codec.decrypt(context, first))
        assertContentEquals(plaintext, codec.decrypt(context, first)) // duplicate decrypt is not freshness
        assertFailsWith<GeneralSecurityException> { codec().decrypt(context, first) }
    }

    @Test fun everyContextDimensionIsAuthenticated() {
        val codec = codec()
        val bytes = codec.encrypt(context, byteArrayOf(1))
        listOf(context.copy(group = "other"), context.copy(historyEpoch = "other"),
            context.copy(profile = "developer"), context.copy(objectKind = "head"),
            context.copy(objectId = "other"), context.copy(publisher = "phone"),
            context.copy(keyGeneration = 2), context.copy(revision = 2)).forEach {
            assertFailsWith<GeneralSecurityException> { codec.decrypt(it, bytes) }
        }
    }

    @Test fun contextEncodingIsUnambiguousAndStrict() {
        assertFalse(context.copy(group = "ab", historyEpoch = "c").authenticatedData().contentEquals(
            context.copy(group = "a", historyEpoch = "bc").authenticatedData()))
        assertFailsWith<IllegalArgumentException> { context.copy(profile = "unknown") }
        assertFailsWith<IllegalArgumentException> { context.copy(revision = 0) }
        assertFailsWith<IllegalArgumentException> { context.copy(keyGeneration = -1) }
        assertFailsWith<IllegalArgumentException> { context.copy(publisher = "../peer") }
    }

    @Test fun strictFramingAndTamperingAreRejected() {
        val codec = codec()
        val bytes = codec.encrypt(context, byteArrayOf(1, 2))
        val altered = bytes.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertFailsWith<GeneralSecurityException> { codec.decrypt(context, altered) }
        listOf(bytes.copyOf(11), bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0),
            bytes.copyOf().also { ByteBuffer.wrap(it).putInt(0) },
            bytes.copyOf().also { ByteBuffer.wrap(it).putInt(4, 2) },
            bytes.copyOf().also { ByteBuffer.wrap(it).putInt(8, Int.MAX_VALUE) },
            bytes.copyOf().also { ByteBuffer.wrap(it).putInt(8, -1) }).forEach {
            assertFailsWith<IllegalArgumentException> { codec.decrypt(context, it) }
        }
    }

    @Test fun boundarySizesAndUnsupportedKeyType() {
        val codec = codec()
        val maximum = ByteArray(EncryptedRemoteEnvelope.MAX_PLAINTEXT_BYTES) { (it % 251).toByte() }
        assertContentEquals(maximum, codec.decrypt(context, codec.encrypt(context, maximum)))
        assertContentEquals(byteArrayOf(), codec.decrypt(context, codec.encrypt(context, byteArrayOf())))
        assertFailsWith<IllegalArgumentException> {
            codec.encrypt(context, ByteArray(EncryptedRemoteEnvelope.MAX_PLAINTEXT_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decrypt(context, ByteArray(EncryptedRemoteEnvelope.MAX_ENVELOPE_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            EncryptedRemoteEnvelope.fromKeyset(KeysetHandle.generateNew(PredefinedAeadParameters.AES128_GCM))
        }
    }
}
