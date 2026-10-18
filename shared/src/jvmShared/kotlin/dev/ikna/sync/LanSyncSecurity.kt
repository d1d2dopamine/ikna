package dev.ikna.sync

import okhttp3.tls.HeldCertificate
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** Only place for private pairing material. Never include this directory in backup/export. */
internal object LanSyncSecurity {
    private val privateFiles = PrivateSyncFiles()

    fun privateDirectory(directory: File) = privateFiles.directory(directory)

    fun readPrivate(file: File, maxBytes: Long): String = privateFiles.read(file, maxBytes)

    fun secret(): String = ByteArray(32).also(SecureRandom()::nextBytes).let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }

    fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    fun certificate(pem: String): X509Certificate {
        require(pem.length <= 4096)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate
    }

    fun id(cert: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(cert.encoded).joinToString("") { "%02x".format(it) }

    fun identity(directory: File): HeldCertificate {
        privateDirectory(directory)
        val file = File(directory, "identity.pem")
        if (file.exists()) {
            return HeldCertificate.decode(readPrivate(file, 8192))
        }
        val identity = HeldCertificate.Builder().commonName("ikna paired device")
            .duration(3650, TimeUnit.DAYS).ecdsa256().build()
        writePrivate(file, identity.certificatePem() + identity.privateKeyPkcs8Pem())
        return identity
    }

    fun writePrivate(file: File, value: String) = privateFiles.write(file, value)

    fun context(identity: HeldCertificate, pin: X509Certificate? = null): SSLContext {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("device", identity.keyPair.private, CharArray(0), arrayOf(identity.certificate))
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(store, CharArray(0))
        }
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                throw java.security.cert.CertificateException("Client certificates are not used")
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                if (pin == null || chain.size != 1 || !MessageDigest.isEqual(pin.encoded, chain[0].encoded)) {
                    throw java.security.cert.CertificateException("Unpaired certificate")
                }
                chain[0].checkValidity()
            }
        }
        return SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf<TrustManager>(trust), SecureRandom()) }
    }

    /** No DNS resolution: literal IPv4 RFC1918/link-local/loopback only in protocol v1. */
    fun address(host: String): InetAddress {
        val parts = host.split('.')
        require(parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) })
        val numbers = parts.map { it.toInt().also { n -> require(n in 0..255) } }
        require(parts.zip(numbers).all { (text, value) -> text == value.toString() })
        require(numbers[0] == 10 || numbers[0] == 127 ||
            (numbers[0] == 172 && numbers[1] in 16..31) ||
            (numbers[0] == 192 && numbers[1] == 168) ||
            (numbers[0] == 169 && numbers[1] == 254))
        return InetAddress.getByAddress(numbers.map(Int::toByte).toByteArray())
    }
}
