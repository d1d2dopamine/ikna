package dev.ikna.buildcheck;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.tls.HeldCertificate;

/** Test-only library smoke. No app files, credentials, remote server or external connection. */
public final class ReleaseDependencySmoke {
    public static void main(String[] args) throws Exception {
        // Same HeldCertificate build/PEM decode operations as LanSyncSecurity.
        HeldCertificate identity = new HeldCertificate.Builder()
                .commonName("ikna release fixture").ecdsa256().build();
        HeldCertificate restored = HeldCertificate.decode(
                identity.certificatePem() + identity.privateKeyPkcs8Pem());
        require(Arrays.equals(identity.certificate().getEncoded(), restored.certificate().getEncoded()),
                "certificate PEM round trip");
        require(Arrays.equals(identity.keyPair().getPrivate().getEncoded(),
                restored.keyPair().getPrivate().getEncoded()), "private key PEM round trip");
        X509Certificate pin = restored.certificate();
        pin.verify(restored.keyPair().getPublic());

        // Use JDK JSSE, with an exact certificate pin, like the LAN transport.
        SSLContext context = context(restored, pin);
        try (SSLServerSocket server = (SSLServerSocket) context.getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            CompletableFuture<Void> accepted = CompletableFuture.runAsync(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(5000);
                    socket.startHandshake();
                    require(socket.getInputStream().read() == 42, "TLS request");
                    socket.getOutputStream().write(43);
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(InetAddress.getLoopbackAddress(), server.getLocalPort())) {
                socket.setSoTimeout(5000);
                socket.startHandshake();
                socket.getOutputStream().write(42);
                require(socket.getInputStream().read() == 43, "TLS response");
            }
            accepted.get(10, TimeUnit.SECONDS);
        }
        System.out.println("PASS: shrunk HeldCertificate PEM and pinned JSSE loopback TLS");

        // Same primitive family and keyset inspection as EncryptedRemoteEnvelope.
        AeadConfig.register();
        KeysetHandle keyset = KeysetHandle.generateNew(PredefinedAeadParameters.XCHACHA20_POLY1305);
        require(keyset.getKeysetInfo().getKeyInfoCount() == 1, "keyset info");
        require(keyset.getKeysetInfo().getKeyInfo(0).getTypeUrl()
                .equals("type.googleapis.com/google.crypto.tink.XChaCha20Poly1305Key"), "key type");
        Aead aead = keyset.getPrimitive(RegistryConfiguration.get(), Aead.class);
        byte[] plaintext = "release fixture".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "authenticated fixture context".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aead.encrypt(plaintext, aad);
        require(Arrays.equals(plaintext, aead.decrypt(ciphertext, aad)), "AEAD round trip");
        ciphertext[ciphertext.length - 1] ^= 1;
        try {
            aead.decrypt(ciphertext, aad);
            throw new AssertionError("tampered ciphertext accepted");
        } catch (java.security.GeneralSecurityException expected) {
            // Authentication must still fail after shrinking.
        }
        System.out.println("PASS: shrunk XChaCha20Poly1305 keyset, round trip and tamper rejection");
    }

    private static SSLContext context(HeldCertificate identity, X509Certificate pin) throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null);
        store.setKeyEntry("device", identity.keyPair().getPrivate(), new char[0],
                new java.security.cert.Certificate[] {identity.certificate()});
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, new char[0]);
        TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                throw new CertificateException("Client certificates unused");
            }
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain.length != 1 || !MessageDigest.isEqual(pin.getEncoded(), chain[0].getEncoded())) {
                    throw new CertificateException("Unpaired certificate");
                }
                chain[0].checkValidity();
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), new TrustManager[] {trust}, new SecureRandom());
        return context;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
