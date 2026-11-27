package dev.ikna.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.test.*

class SyncRelayClientTest {
    private val binding = HistoryBinding("relay-test-history", "epoch-1")

    /** Pure java.net.ServerSocket HTTP/1.1 mock relay (portable across JDK 17 and Android host unit tests). */
    private class InMemoryRelayServer : AutoCloseable {
        private val queues = ConcurrentHashMap<String, MutableList<String>>()
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val pool = Executors.newCachedThreadPool { r -> Thread(r, "test-relay").apply { isDaemon = true } }
        @Volatile private var running = true
        val baseUrl: String get() = "http://127.0.0.1:${server.localPort}"

        init {
            pool.submit {
                while (running) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    pool.submit { handle(socket) }
                }
            }
        }

        private fun handle(socket: java.net.Socket) {
            socket.use { s ->
                s.soTimeout = 10_000
                val input = BufferedInputStream(s.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0]
                val rawTarget = parts[1]
                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0 && line.substring(0, colon).equals("Content-Length", ignoreCase = true)) {
                        contentLength = line.substring(colon + 1).trim().toIntOrNull() ?: 0
                    }
                }
                val qIdx = rawTarget.indexOf('?')
                val path = if (qIdx >= 0) rawTarget.substring(0, qIdx) else rawTarget
                val queryStr = if (qIdx >= 0) rawTarget.substring(qIdx + 1) else ""
                val query = queryStr.split("&").mapNotNull { kv ->
                    val eq = kv.indexOf('=')
                    if (eq > 0) kv.substring(0, eq) to kv.substring(eq + 1) else null
                }.toMap()
                val match = Regex("^/v1/room/([a-f0-9]{64})/(send|poll)$").matchEntire(path)
                val out = s.getOutputStream()
                if (match == null) {
                    writeResponse(out, 404, "Not Found", ByteArray(0))
                    return
                }
                val channel = match.groupValues[1]
                val action = match.groupValues[2]
                if (action == "send" && method == "POST") {
                    val bodyBytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = input.read(bodyBytes, read, contentLength - read)
                        if (n < 0) break
                        read += n
                    }
                    val slot = query["to"].orEmpty()
                    val key = "$channel:$slot"
                    val list = queues.computeIfAbsent(key) { mutableListOf() }
                    synchronized(list) { list.add(bodyBytes.toString(Charsets.UTF_8)) }
                    writeResponse(out, 202, "Accepted", ByteArray(0))
                } else if (action == "poll" && method == "GET") {
                    val slot = query["slot"].orEmpty()
                    val waitMs = (query["wait"]?.toLongOrNull() ?: 0L).coerceIn(0L, 1_500L)
                    val key = "$channel:$slot"
                    val deadline = System.currentTimeMillis() + waitMs
                    var popped: List<String> = emptyList()
                    while (running) {
                        val list = queues[key]
                        if (list != null) {
                            synchronized(list) {
                                if (list.isNotEmpty()) {
                                    popped = list.toList()
                                    list.clear()
                                }
                            }
                        }
                        if (popped.isNotEmpty() || System.currentTimeMillis() >= deadline) break
                        Thread.sleep(25)
                    }
                    if (popped.isEmpty()) {
                        writeResponse(out, 204, "No Content", ByteArray(0))
                    } else {
                        val body = ("[" + popped.joinToString(",") + "]").toByteArray(Charsets.UTF_8)
                        writeResponse(out, 200, "OK", body, "application/json; charset=utf-8")
                    }
                } else {
                    writeResponse(out, 405, "Method Not Allowed", ByteArray(0))
                }
            }
        }

        private fun readLine(input: BufferedInputStream): String? {
            val sb = StringBuilder()
            var prev = -1
            while (true) {
                val b = input.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) {
                    if (prev == '\r'.code && sb.isNotEmpty()) sb.setLength(sb.length - 1)
                    return sb.toString()
                }
                sb.append(b.toChar())
                prev = b
            }
        }

        private fun writeResponse(
            out: java.io.OutputStream,
            code: Int,
            reason: String,
            body: ByteArray,
            contentType: String? = null,
        ) {
            val header = buildString {
                append("HTTP/1.1 $code $reason\r\n")
                if (contentType != null) append("Content-Type: $contentType\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(Charsets.US_ASCII))
            if (body.isNotEmpty()) out.write(body)
            out.flush()
        }

        override fun close() {
            running = false
            runCatching { server.close() }
            pool.shutdownNow()
        }
    }

    private class Data(private val payload: ByteArray) : PairedLanTransport.PayloadCallbacks {
        val received = mutableListOf<String>()
        override suspend fun snapshot() = payload
        override suspend fun receive(payload: ByteArray) {
            received += payload.toString(Charsets.UTF_8)
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("ikna-relay-test-").toFile()

    @Test fun `relay URL validation accepts HTTPS and loopback and rejects remote plaintext HTTP`() {
        assertEquals("", SyncRelayClient.normalizeRelayUrl("   "))
        assertEquals("https://ikna-relay.workers.dev", SyncRelayClient.normalizeRelayUrl("https://ikna-relay.workers.dev/"))
        assertEquals("http://127.0.0.1:8787", SyncRelayClient.normalizeRelayUrl("http://127.0.0.1:8787/"))
        assertFails { SyncRelayClient.normalizeRelayUrl("http://example.com") }
        assertFails { SyncRelayClient.normalizeRelayUrl("ftp://example.com") }
        assertFails { SyncRelayClient.normalizeRelayUrl("https://user:pass@example.com") }
        assertFails { SyncRelayClient.normalizeRelayUrl("https://example.com?foo=bar") }
    }

    @Test fun `relay AEAD encryption authenticates channel and sender slot and rejects tampering`() {
        val secret = LanSyncSecurity.secret()
        val chan = SyncRelayClient.channelId("sync:g1", secret)
        val key = SyncRelayClient.deriveKey("sync:g1", secret)
        val plaintext = "ikna-secret-sync-payload".toByteArray(Charsets.UTF_8)
        val sealed = SyncRelayClient.encrypt(key, chan, "slot-a", plaintext)

        assertContentEquals(plaintext, SyncRelayClient.decrypt(key, chan, "slot-a", sealed))
        // Direction/reflection binding: replaying sender's own frame under another slot must fail.
        assertFailsWith<GeneralSecurityException> { SyncRelayClient.decrypt(key, chan, "slot-b", sealed) }
        // Wrong key must fail.
        val otherKey = SyncRelayClient.deriveKey("sync:g1", LanSyncSecurity.secret())
        assertFailsWith<GeneralSecurityException> { SyncRelayClient.decrypt(otherKey, chan, "slot-a", sealed) }
        // Single-bit ciphertext tamper must fail.
        val tampered = sealed.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0x01).toByte() }
        assertFailsWith<GeneralSecurityException> { SyncRelayClient.decrypt(key, chan, "slot-a", tampered) }
    }

    @Test fun `pairing and large compressed payload exchange succeed through E2EE relay when LAN is unreachable`() = runBlocking {
        val dirA = tempDir(); val dirB = tempDir()
        val largeA = "{\"from\":\"A\",\"items\":[" + List(12_000) { "\"word-a-$it\"" }.joinToString(",") + "]}"
        val largeB = "{\"from\":\"B\",\"items\":[" + List(12_000) { "\"word-b-$it\"" }.joinToString(",") + "]}"
        val aData = Data(largeA.toByteArray(Charsets.UTF_8))
        val bData = Data(largeB.toByteArray(Charsets.UTF_8))
        val relay = InMemoryRelayServer()
        val a = PairedLanTransport(dirA, binding, aData, intervalMillis = 150)
        val b = PairedLanTransport(dirB, binding, bData, intervalMillis = 150)
        try {
            a.start(); b.start()
            // Relay-only invitation (no LAN host): forces pairing and exchange purely over E2EE relay.
            val rawInvite = a.createInvitation("", relayUrl = relay.baseUrl)
            val qrV2 = PairedLanTransport.encodeQrInvitation(rawInvite)
            val parsed = PairedLanTransport.parseInvitation(qrV2)
            assertEquals(relay.baseUrl, parsed.relayUrl)
            assertEquals("", parsed.host)

            b.pair(qrV2)
            withTimeout(15_000) {
                while (!aData.received.contains(largeB) || !bData.received.contains(largeA)) delay(50)
            }
            assertEquals(1, a.status.peerCount)
            assertEquals(1, b.status.peerCount)
            assertTrue(a.status.peers.single().hasRelay)
            assertTrue(b.status.peers.single().hasRelay)
            assertEquals(PairedLanTransport.PeerRole.INVITER, a.status.peers.single().role)
            assertEquals(PairedLanTransport.PeerRole.JOINER, b.status.peers.single().role)
        } finally {
            a.close(); b.close(); relay.close()
            dirA.deleteRecursively(); dirB.deleteRecursively()
        }
    }

    @Test fun `manual only mode stays idle until sync now is triggered`() = runBlocking {
        val dirA = tempDir(); val dirB = tempDir()
        val aData = Data("manual-A".toByteArray())
        val bData = Data("manual-B".toByteArray())
        var auto = false
        val a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100, autoPoll = { auto })
        val b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100, autoPoll = { auto })
        try {
            a.start(); b.start()
            b.pair(a.createInvitation("127.0.0.1"))
            // Initial pairing sets syncNowRequested once, wait for that exchange then clear.
            withTimeout(5_000) { while (aData.received.isEmpty() || bData.received.isEmpty()) delay(25) }
            delay(250)
            val countBefore = aData.received.size
            delay(450)
            // With autoPoll = false and no triggerSyncNow, no background polls occur.
            assertEquals(countBefore, aData.received.size)

            // Manual trigger immediately performs an exchange even with autoPoll = false.
            b.triggerSyncNow()
            withTimeout(5_000) { while (aData.received.size <= countBefore) delay(25) }
            assertTrue(aData.received.size > countBefore)
        } finally {
            a.close(); b.close()
            dirA.deleteRecursively(); dirB.deleteRecursively()
        }
    }
}
