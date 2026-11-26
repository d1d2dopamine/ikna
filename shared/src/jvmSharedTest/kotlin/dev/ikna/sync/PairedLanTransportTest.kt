package dev.ikna.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairedLanTransportTest {
    private val binding = HistoryBinding("test-history", "epoch-1")
    private class Data(@Volatile var outgoing: ByteArray) : PairedLanTransport.PayloadCallbacks {
        val received = CopyOnWriteArrayList<String>()
        val authenticatedPeers = CopyOnWriteArrayList<String>()
        override suspend fun snapshotFor(peerId: String): ByteArray { authenticatedPeers += peerId; return snapshot() }
        override suspend fun receiveFrom(peerId: String, payload: ByteArray) { authenticatedPeers += peerId; receive(payload) }
        override suspend fun snapshot() = outgoing
        override suspend fun receive(payload: ByteArray) { received += payload.toString(Charsets.UTF_8) }
    }
    private fun temp(): File = Files.createTempDirectory("ikna-lan-test").toFile()
    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(25)
        assertTrue(condition(), "Timed out waiting for local exchange")
    }

    @Test fun `invitation diagnostic string never exposes access material`() {
        val secret = LanSyncSecurity.secret()
        val invitation = PairingInvitation(host = "192.168.1.2", port = 12345,
            certificate = "private-certificate-placeholder", binding = binding,
            secret = secret, expiresAtMillis = System.currentTimeMillis() + 300_000)
        val diagnostic = invitation.toString()
        assertTrue(diagnostic.contains("<redacted>"))
        assertFalse(diagnostic.contains(secret))
        assertFalse(diagnostic.contains(invitation.certificate))
        assertFalse(diagnostic.contains(invitation.host))
    }

    @Test fun `literal endpoint validation never resolves public hosts`() {
        listOf("localhost", "example.com", "8.8.8.8", "0.0.0.0", "192.168.1.01", "127.1", "10.1.1.256", "::1").forEach {
            assertFails(it) { LanSyncSecurity.address(it) }
        }
        listOf("127.0.0.1", "10.2.3.4", "172.16.0.1", "192.168.1.2", "169.254.2.3").forEach {
            assertEquals(it, LanSyncSecurity.address(it).hostAddress)
        }
    }

    @Test fun `explicit pairing automatically exchanges and survives restart`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val aData = Data("from-A".toByteArray()); val bData = Data("from-B".toByteArray())
        var a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        var b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            assertFalse(a.status.running)
            assertFails { a.createInvitation("127.0.0.1") }
            val portA = a.start(); val portB = b.start()
            val invitation = a.createInvitation("127.0.0.1")
            b.pair(invitation)
            waitFor { aData.received.contains("from-B") && bData.received.contains("from-A") }
            assertEquals(1, a.status.peerCount); assertEquals(1, b.status.peerCount)
            assertFails { b.pair(invitation) }
            val idA = a.deviceId
            a.close(); b.close()
            aData.received.clear(); bData.received.clear()
            a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
            b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
            assertEquals(idA, a.deviceId)
            assertEquals(portA, a.start()); assertEquals(portB, b.start())
            waitFor { aData.received.contains("from-B") && bData.received.contains("from-A") }
            a.unpairAll()
            val count = aData.received.size
            Thread.sleep(350)
            assertEquals(count, aData.received.size)
            assertEquals(0, a.status.peerCount)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `wrong pin secret expiry and history cannot pair`() = runBlocking {
        val dirA = temp(); val dirB = temp(); val dirC = temp()
        val data = Data(ByteArray(0))
        val a = PairedLanTransport(dirA, binding, data, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, data, intervalMillis = 100)
        try {
            a.start(); b.start()
            val invitation = PairedLanTransport.parseInvitation(a.createInvitation("127.0.0.1"))
            val wrongCert = LanSyncSecurity.identity(dirC).certificatePem()
            assertFails { b.pair(Json.encodeToString(invitation.copy(certificate = wrongCert))) }
            assertFails { b.pair(Json.encodeToString(invitation.copy(secret = LanSyncSecurity.secret()))) }
            assertFails { b.pair(Json.encodeToString(invitation.copy(expiresAtMillis = 1))) }
            assertFails { b.pair(Json.encodeToString(invitation.copy(binding = HistoryBinding("different", "epoch-1")))) }
            assertEquals(0, a.status.peerCount); assertEquals(0, b.status.peerCount)
            b.pair(Json.encodeToString(invitation))
            assertEquals(1, a.status.peerCount)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively(); dirC.deleteRecursively() }
    }

    @Test fun `oversized payload never reaches receiver and changed binding fails closed`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val aData = Data(ByteArray(0)); val bData = Data(ByteArray(PairedLanTransport.MAX_PAYLOAD + 1))
        val a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            a.start(); b.start(); b.pair(a.createInvitation("127.0.0.1"))
            waitFor { b.status.lastError != null }
            assertTrue(aData.received.none { it.length > PairedLanTransport.MAX_PAYLOAD })
            assertFails { PairedLanTransport(dirA, HistoryBinding("test-history", "epoch-2"), aData) }
            a.unpairAll()
            PairedLanTransport(dirA, HistoryBinding("test-history", "epoch-2"), aData).close()
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }
    @Test fun `invitation is consumed once and wrong token cannot invoke callbacks`() = runBlocking {
        val dirs = List(3) { temp() }
        val data = List(3) { Data("ok".toByteArray()) }
        val transports = dirs.mapIndexed { i, dir -> PairedLanTransport(dir, binding, data[i], intervalMillis = 100) }
        try {
            transports.forEach { it.start() }
            val inviteText = transports[0].createInvitation("127.0.0.1")
            val invite = PairedLanTransport.parseInvitation(inviteText)
            transports[1].pair(inviteText)
            assertFails { transports[2].pair(inviteText) }
            assertEquals(1, transports[0].status.peerCount)
            // A correctly server-pinned TLS connection is still unauthorized without a peer credential.
            transports[1].stop()
            Thread.sleep(150)
            val before = data[0].received.size
            val context = LanSyncSecurity.context(LanSyncSecurity.identity(dirs[2]), LanSyncSecurity.certificate(invite.certificate))
            (context.socketFactory.createSocket("127.0.0.1", invite.port) as javax.net.ssl.SSLSocket).use { socket ->
                socket.soTimeout = 2000
                socket.startHandshake()
                val input = java.io.DataInputStream(socket.inputStream)
                val challengeSize = input.readInt()
                val challenge = ByteArray(challengeSize).also(input::readFully).toString(Charsets.UTF_8)
                val frame = """{"version":1,"operation":"exchange","binding":{"groupId":"test-history","epoch":"epoch-1"},"id":"${transports[1].deviceId}","credential":"${LanSyncSecurity.secret()}","challenge":"$challenge","payload":"aW52YWxpZA=="}""".toByteArray()
                java.io.DataOutputStream(socket.outputStream).apply { writeInt(frame.size); write(frame); flush() }
                assertFails { input.readInt() }
            }
            assertEquals(before, data[0].received.size)
        } finally { transports.forEach { it.close() }; dirs.forEach { it.deleteRecursively() } }
    }

    @Test fun `stop cancels active receive and prevents later import`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val entered = java.util.concurrent.CountDownLatch(1)
        var imported = false
        val slow = object : PairedLanTransport.PayloadCallbacks {
            override suspend fun snapshot() = ByteArray(0)
            override suspend fun receive(payload: ByteArray) {
                entered.countDown()
                kotlinx.coroutines.delay(60_000)
                imported = true
            }
        }
        val a = PairedLanTransport(dirA, binding, slow, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, Data("incoming".toByteArray()), intervalMillis = 100)
        try {
            a.start(); b.start(); b.pair(a.createInvitation("127.0.0.1"))
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            a.stop()
            assertFalse(imported)
            assertFalse(a.status.running)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `pairing attempts are bounded and stalled handshake times out`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val a = PairedLanTransport(dirA, binding, Data(ByteArray(0)), intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, Data(ByteArray(0)), intervalMillis = 100)
        try {
            val port = a.start(); b.start()
            // A peer that never begins TLS cannot occupy the only server worker indefinitely.
            java.net.Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 8_000
                val started = System.nanoTime()
                // JSSE may send an alert on failure, so consume to EOF.
                try { while (socket.inputStream.read() != -1) { } } catch (_: java.net.SocketException) { /* reset is also closed */ }
                assertTrue(System.nanoTime() - started < 8_000_000_000)
            }
            val invite = PairedLanTransport.parseInvitation(a.createInvitation("127.0.0.1"))
            repeat(8) { assertFails { b.pair(Json.encodeToString(invite.copy(secret = LanSyncSecurity.secret()))) } }
            assertFails { b.pair(Json.encodeToString(invite)) }
            assertEquals(0, a.status.peerCount)
            b.pair(a.createInvitation("127.0.0.1"))
            assertEquals(1, a.status.peerCount)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `oversized frame header is rejected before allocation`() {
        val dirA = temp(); val dirB = temp()
        val data = Data(ByteArray(0))
        val a = PairedLanTransport(dirA, binding, data, intervalMillis = 100)
        try {
            a.start()
            val invite = PairedLanTransport.parseInvitation(a.createInvitation("127.0.0.1"))
            val context = LanSyncSecurity.context(LanSyncSecurity.identity(dirB), LanSyncSecurity.certificate(invite.certificate))
            (context.socketFactory.createSocket("127.0.0.1", invite.port) as javax.net.ssl.SSLSocket).use { socket ->
                socket.soTimeout = 2000
                socket.startHandshake()
                val input = java.io.DataInputStream(socket.inputStream)
                val challengeSize = input.readInt()
                input.readFully(ByteArray(challengeSize))
                java.io.DataOutputStream(socket.outputStream).apply { writeInt(Int.MAX_VALUE); flush() }
                assertFails { input.readInt() }
            }
            assertTrue(data.received.isEmpty())
            assertEquals(0, a.status.peerCount)
        } finally { a.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `individual revoke preserves second peer across restart without late imports`() = runBlocking {
        val dirs = List(3) { temp() }
        val data = List(3) { Data("peer-$it".toByteArray()) }
        var a = PairedLanTransport(dirs[0], binding, data[0], intervalMillis = 100)
        val b = PairedLanTransport(dirs[1], binding, data[1], intervalMillis = 100)
        val c = PairedLanTransport(dirs[2], binding, data[2], intervalMillis = 100)
        try {
            a.start(); b.start(); c.start()
            b.pair(a.createInvitation("127.0.0.1"))
            c.pair(a.createInvitation("127.0.0.1"))
            waitFor { a.status.peers.size == 2 && a.status.peers.all { it.lastSuccessAtMillis != null } }
            assertEquals(setOf(b.deviceId, c.deviceId), data[0].authenticatedPeers.toSet())
            assertEquals(setOf(a.deviceId), data[1].authenticatedPeers.toSet())
            assertEquals(setOf(a.deviceId), data[2].authenticatedPeers.toSet())
            val view = a.status.peers.single { it.id == b.deviceId }
            assertEquals(b.deviceId, view.fingerprint.replace(" ", ""))
            assertTrue(view.address.startsWith("127.0.0.1:"))
            val savedBefore = File(dirs[0], "peers.json").readText()
            assertFalse(view.toString().contains(b.deviceId))
            assertFalse(view.toString().contains(view.address))
            assertFalse(a.status.toString().contains("credential"))
            a.revokePeer(b.deviceId)
            assertTrue(a.status.running)
            assertEquals(listOf(c.deviceId), a.status.peers.map { it.id })
            val afterRevokeB = data[0].received.count { it == "peer-1" }
            data[2].outgoing = "retained-live".toByteArray()
            waitFor { data[0].received.contains("retained-live") }
            assertEquals(afterRevokeB, data[0].received.count { it == "peer-1" })
            assertFalse(File(dirs[0], "peers.json").readText().contains(b.deviceId))
            assertTrue(savedBefore.contains(b.deviceId))
            a.close()
            a = PairedLanTransport(dirs[0], binding, data[0], intervalMillis = 100)
            assertEquals(listOf(c.deviceId), a.status.peers.map { it.id })
            assertEquals(null, a.status.peers.single().lastSuccessAtMillis)
            assertEquals(null, a.status.peers.single().lastError)
            a.start()
            data[2].outgoing = "retained-after-restart".toByteArray()
            waitFor { data[0].received.contains("retained-after-restart") }
            assertEquals(afterRevokeB, data[0].received.count { it == "peer-1" })
            a.stop()
            a.revokePeer(c.deviceId)
            assertFalse(a.status.running)
            assertEquals(0, a.status.peerCount)
            assertFails { a.revokePeer(c.deviceId) }
            Unit
        } finally { a.close(); b.close(); c.close(); dirs.forEach { it.deleteRecursively() } }
    }

    @Test fun `individual revoke cancels entered callback before removing authorization`() = runBlocking {
        val dirs = List(2) { temp() }
        val entered = java.util.concurrent.CountDownLatch(1)
        val cancelled = java.util.concurrent.CountDownLatch(1)
        val imported = java.util.concurrent.atomic.AtomicBoolean(false)
        val slow = object : PairedLanTransport.PayloadCallbacks {
            override suspend fun snapshot() = ByteArray(0)
            override suspend fun receive(payload: ByteArray) {
                entered.countDown()
                try {
                    kotlinx.coroutines.delay(60_000)
                    imported.set(true)
                } finally {
                    // Authorization must still be present while cancellation unwinds.
                    assertTrue(File(dirs[0], "peers.json").readText().contains("credential"))
                    cancelled.countDown()
                }
            }
        }
        val a = PairedLanTransport(dirs[0], binding, slow, intervalMillis = 100)
        val b = PairedLanTransport(dirs[1], binding, Data("incoming".toByteArray()), intervalMillis = 100)
        try {
            a.start(); b.start(); b.pair(a.createInvitation("127.0.0.1"))
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            a.revokePeer(b.deviceId)
            assertEquals(0L, cancelled.count)
            assertFalse(imported.get())
            assertTrue(a.status.running)
            assertEquals(0, a.status.peerCount)
            Thread.sleep(350)
            assertFalse(imported.get())
        } finally { a.close(); b.close(); dirs.forEach { it.deleteRecursively() } }
    }

    @Test fun `profile resource rejection is safe and exchange can resume without re pairing`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val reject = java.util.concurrent.atomic.AtomicBoolean(true)
        val received = CopyOnWriteArrayList<String>()
        val bounded = object : PairedLanTransport.PayloadCallbacks {
            override suspend fun snapshot(): ByteArray {
                if (reject.get()) throw dev.ikna.data.export.ProfileTransferLimitException(
                    dev.ikna.data.export.ProfileTransferLimitException.Limit.BYTES)
                return "synthetic-ok".toByteArray()
            }
            override suspend fun receive(payload: ByteArray) { received += payload.toString(Charsets.UTF_8) }
        }
        val other = Data("synthetic-peer".toByteArray())
        val a = PairedLanTransport(dirA, binding, bounded, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, other, intervalMillis = 100)
        try {
            a.start(); b.start(); b.pair(a.createInvitation("127.0.0.1"))
            waitFor { a.status.lastError == "payload_too_large" }
            assertEquals(1, a.status.peerCount)
            assertFalse(other.received.contains("synthetic-ok"))
            reject.set(false)
            waitFor { other.received.contains("synthetic-ok") && received.contains("synthetic-peer") }
            assertEquals(1, a.status.peerCount)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `incompatible profile protocol is reported without exposing payload or revoking trust`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val callbacks = object : PairedLanTransport.PayloadCallbacks {
            override suspend fun snapshot(): ByteArray = throw UnsupportedSyncProtocolException()
            override suspend fun receive(payload: ByteArray) = Unit
        }
        val a = PairedLanTransport(dirA, binding, callbacks, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, Data("__TECHNICAL_OLD_PEER__".toByteArray()), intervalMillis = 100)
        try {
            a.start(); b.start(); b.pair(a.createInvitation("127.0.0.1"))
            waitFor { a.status.lastError == "unsupported_sync_protocol" }
            assertEquals(1, a.status.peerCount)
            assertEquals("unsupported_sync_protocol", a.status.peers.single().lastError)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `overlay VPN CGNAT addresses are allowed while public space stays rejected`() {
        listOf("100.64.0.1", "100.100.100.100", "100.127.255.254").forEach {
            assertEquals(it, LanSyncSecurity.address(it).hostAddress)
            assertFalse(LanSyncSecurity.isDirectLanAddress(LanSyncSecurity.address(it)))
        }
        listOf("100.63.255.255", "100.128.0.1").forEach {
            assertFails(it) { LanSyncSecurity.address(it) }
        }
        assertTrue(LanSyncSecurity.isDirectLanAddress(LanSyncSecurity.address("192.168.1.10")))
        assertFalse(LanSyncSecurity.isDirectLanAddress(LanSyncSecurity.address("127.0.0.1")))
    }

    @Test fun `VPN and virtual interfaces rank behind physical LAN interfaces`() {
        assertTrue(PairedLanTransport.isLikelyVpnOrVirtualInterface("tun0", "WireGuard Tunnel"))
        assertTrue(PairedLanTransport.isLikelyVpnOrVirtualInterface("eth3", "vEthernet (WSL)"))
        assertTrue(PairedLanTransport.isLikelyVpnOrVirtualInterface("utun4", "Tailscale"))
        assertFalse(PairedLanTransport.isLikelyVpnOrVirtualInterface("wlan0", "Wi-Fi"))
        assertFalse(PairedLanTransport.isLikelyVpnOrVirtualInterface("eth0", "Intel Ethernet"))

        val wifiRank = PairedLanTransport.rankCandidateAddress("192.168.1.42", isVpnOrVirtual = false)
        val vpnOverlayRank = PairedLanTransport.rankCandidateAddress("100.64.0.5", isVpnOrVirtual = true)
        val vpnVirtualLanRank = PairedLanTransport.rankCandidateAddress("192.168.50.1", isVpnOrVirtual = true)
        assertTrue(wifiRank < vpnOverlayRank)
        assertTrue(vpnOverlayRank < vpnVirtualLanRank)
    }

    @Test fun `multi-host invitation falls back cleanly when primary host is unreachable`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val aData = Data("fallback-A".toByteArray()); val bData = Data("fallback-B".toByteArray())
        val a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            // Bind specifically to 127.0.0.1 so 127.0.0.2 is refused on Linux as well as Windows.
            a.start(bindAddress = java.net.InetAddress.getByName("127.0.0.1")); b.start()
            val rawInvite = PairedLanTransport.parseInvitation(a.createInvitation("127.0.0.1"))
            // Primary host refuses connection on this port; fallback is 127.0.0.1.
            val multiHostInvite = rawInvite.copy(host = "127.0.0.2", hosts = listOf("127.0.0.2", "127.0.0.1"))
            b.pair(Json.encodeToString(multiHostInvite))
            waitFor { aData.received.contains("fallback-B") && bData.received.contains("fallback-A") }
            assertEquals(1, b.status.peerCount)
            assertTrue(b.status.peers.single().address.startsWith("127.0.0.1:"))
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `inviter and joiner roles persist across restart and invalid or expired invitations report specific codes`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val aData = Data("from-A".toByteArray()); val bData = Data("from-B".toByteArray())
        var a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        var b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            a.start(); b.start()
            val invalidErr = kotlin.test.assertFailsWith<SyncOperationException> {
                PairedLanTransport.parseInvitation("not-a-valid-ikna-invitation")
            }
            assertEquals("invalid_invitation", invalidErr.code)

            val inviteText = a.createInvitation("127.0.0.1")
            val parsed = PairedLanTransport.parseInvitation(inviteText)
            val expiredErr = kotlin.test.assertFailsWith<SyncOperationException> {
                PairedLanTransport.parseInvitation(Json.encodeToString(parsed.copy(expiresAtMillis = 1L)))
            }
            assertEquals("expired_invitation", expiredErr.code)

            b.pair(inviteText)
            waitFor { aData.received.contains("from-B") && bData.received.contains("from-A") }
            assertEquals(PairedLanTransport.PeerRole.INVITER, a.status.peers.single().role)
            assertEquals(PairedLanTransport.PeerRole.JOINER, b.status.peers.single().role)

            a.close(); b.close()
            a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
            b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
            assertEquals(PairedLanTransport.PeerRole.INVITER, a.status.peers.single().role)
            assertEquals(PairedLanTransport.PeerRole.JOINER, b.status.peers.single().role)
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `compact v2 fingerprint qr pairs over pinned TLS rejects tampered pin and survives restart`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val aData = Data("v2-from-A".toByteArray()); val bData = Data("v2-from-B".toByteArray())
        var a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        var b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            a.start(); b.start()
            val rawInvite = a.createInvitation("127.0.0.1")
            val qrV2 = PairedLanTransport.encodeQrInvitation(rawInvite)
            assertTrue(qrV2.startsWith(PairedLanTransport.COMPACT_QR_PREFIX))
            assertTrue(qrV2.length < rawInvite.length / 2, "QR v2 must drop the 700B PEM cert (${qrV2.length} vs ${rawInvite.length})")
            val qrMatrix = QrCodeEncoder.encode(qrV2, QrErrorCorrection.M)
            assertTrue(qrMatrix.size <= 69, "QR v2 Level-M matrix must stay low-density for monitor scanning (was ${qrMatrix.size})")

            val parsedV2 = PairedLanTransport.parseInvitation(qrV2)
            assertEquals("", parsedV2.certificate)
            assertEquals(64, parsedV2.certPin.length)

            // Tampered fingerprint must be rejected during TLS pin check.
            val badPinInvite = Json.encodeToString(parsedV2.copy(certPin = "0".repeat(64)))
            assertFails { b.pair(badPinInvite) }
            assertEquals(0, b.status.peerCount)

            // Valid QR v2 pairs cleanly and persists full verified peer cert across restart.
            b.pair(qrV2)
            waitFor { aData.received.contains("v2-from-B") && bData.received.contains("v2-from-A") }
            assertEquals(1, a.status.peerCount)
            assertEquals(1, b.status.peerCount)
            assertEquals(PairedLanTransport.PeerRole.INVITER, a.status.peers.single().role)
            assertEquals(PairedLanTransport.PeerRole.JOINER, b.status.peers.single().role)

            a.close(); b.close()
            aData.received.clear(); bData.received.clear()
            a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
            b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
            a.start(); b.start()
            waitFor { aData.received.contains("v2-from-B") && bData.received.contains("v2-from-A") }
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

    @Test fun `large compressible JSON payload exchanges cleanly with gzip compression`() = runBlocking {
        val dirA = temp(); val dirB = temp()
        val bigJsonA = "{\"packs\":[" + List(20_000) { "{\"id\":\"deck-a-$it\",\"text\":\"sample phrase $it\"}" }.joinToString(",") + "]}"
        val bigJsonB = "{\"packs\":[" + List(20_000) { "{\"id\":\"deck-b-$it\",\"text\":\"other phrase $it\"}" }.joinToString(",") + "]}"
        val aData = Data(bigJsonA.toByteArray(Charsets.UTF_8))
        val bData = Data(bigJsonB.toByteArray(Charsets.UTF_8))
        val a = PairedLanTransport(dirA, binding, aData, intervalMillis = 100)
        val b = PairedLanTransport(dirB, binding, bData, intervalMillis = 100)
        try {
            a.start(); b.start()
            b.pair(a.createInvitation("127.0.0.1"))
            waitFor { aData.received.contains(bigJsonB) && bData.received.contains(bigJsonA) }
            assertEquals(bigJsonB, aData.received.last())
            assertEquals(bigJsonA, bData.received.last())
        } finally { a.close(); b.close(); dirA.deleteRecursively(); dirB.deleteRecursively() }
    }

}
