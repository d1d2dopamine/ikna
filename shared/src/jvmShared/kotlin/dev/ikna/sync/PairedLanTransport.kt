package dev.ikna.sync

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Base64
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@Serializable
data class HistoryBinding(val groupId: String, val epoch: String, val freshStart: Boolean = false) {
    init { require(groupId.matches(Regex("[a-zA-Z0-9_-]{1,128}")) && epoch.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) }
}

/** Contains access-granting secret. Show only for deliberate pairing; never log or export it. */
@Serializable
data class PairingInvitation(
    val version: Int = 1, val host: String, val port: Int, val certificate: String,
    val binding: HistoryBinding, val secret: String, val expiresAtMillis: Long,
    val deviceName: String = "",
) {
    override fun toString(): String = "PairingInvitation(version=$version, accessGrant=<redacted>)"
}

/** Foreground-only LAN exchange. All application data remains opaque to the transport. */
class PairedLanTransport(
    private val privateDirectory: File,
    val binding: HistoryBinding,
    private val callbacks: PayloadCallbacks,
    private val onStatus: (Status) -> Unit = {},
    private val intervalMillis: Long = 15_000,
    private val localDeviceName: () -> String = { defaultDeviceDisplayName() },
) : AutoCloseable {
    interface PayloadCallbacks {
        /** Called off the UI thread; implementations must cooperate with coroutine cancellation. */
        suspend fun snapshot(): ByteArray
        suspend fun receive(payload: ByteArray)
        suspend fun snapshotFor(peerId: String): ByteArray = snapshot()
        suspend fun receiveFrom(peerId: String, payload: ByteArray) = receive(payload)
    }
    /** Public view contains no certificate or credential; times describe this foreground session only. */
    data class PeerStatus(
        val id: String, val fingerprint: String, val address: String,
        val lastSuccessAtMillis: Long? = null, val lastError: String? = null,
        val name: String = "",
    ) {
        override fun toString(): String = "PeerStatus(identity=<redacted>, address=<redacted>, lastSuccessAtMillis=$lastSuccessAtMillis, lastError=$lastError)"
    }
    data class Status(val running: Boolean, val peerCount: Int, val lastError: String?,
        val lastSuccessAtMillis: Long? = null, val peers: List<PeerStatus> = emptyList())
    @Serializable private data class Peer(
        val id: String, val host: String, val port: Int, val certificate: String, val credential: String,
        val name: String = "",
    )
    @Serializable private data class Saved(val version: Int = 1, val binding: HistoryBinding, val peers: List<Peer>)
    @Serializable private data class Wire(
        val version: Int = 1, val operation: String, val binding: HistoryBinding,
        val id: String, val credential: String, val challenge: String,
        val certificate: String = "", val port: Int = 0, val payload: String = "",
        val deviceName: String = "",
    )
    private class PayloadTooLargeException : IllegalArgumentException()
    private data class Pending(val invitation: PairingInvitation, val deadlineNanos: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INVITATION_MS), var attempts: Int = 0)
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }
    private val identity = LanSyncSecurity.identity(privateDirectory)
    val deviceId: String = LanSyncSecurity.id(identity.certificate)
    private val gate = Any()
    private val callbackGate = Any()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    private val workers = Executors.newFixedThreadPool(2) { r -> Thread(r, "ikna-lan-sync").apply { isDaemon = true } }
    private val timer = Executors.newScheduledThreadPool(1) { r -> Thread(r, "ikna-lan-timeout").apply { isDaemon = true } }
    private var listener: SSLServerSocket? = null
    private var pending: Pending? = null
    private var generation = 0L
    private var closed = false
    private val peers = mutableListOf<Peer>()
    private val peerResults = mutableMapOf<String, Pair<Long?, String?>>()
    @Volatile private var running = false
    @Volatile private var lastError: String? = null
    @Volatile private var lastSuccessAtMillis: Long? = null
    val status: Status get() = synchronized(gate) { Status(running, peers.size, lastError, lastSuccessAtMillis,
        peers.map { peer -> PeerStatus(peer.id, peer.id.chunked(4).joinToString(" "), "${peer.host}:${peer.port}",
            peerResults[peer.id]?.first, peerResults[peer.id]?.second, peer.name) }) }

    init {
        require(intervalMillis in 100..300_000)
        val saved = File(privateDirectory, "peers.json")
        if (saved.exists()) {
            require(saved.length() <= 32_768) { "Invalid pairing state" }
            val state = json.decodeFromString<Saved>(LanSyncSecurity.readPrivate(saved, 32_768))
            require(state.version == 1 && (state.binding == binding || state.peers.isEmpty()) && state.peers.size <= MAX_PEERS) { "Pairing history changed" }
            state.peers.forEach(::validatePeer)
            require(state.peers.map { it.id }.distinct().size == state.peers.size)
            peers.addAll(state.peers)
        }
    }

    /** Never called implicitly: caller must have persisted explicit user opt-in. */
    @Synchronized fun start(port: Int = 0): Int = synchronized(gate) {
        check(!closed)
        listener?.let { return@synchronized it.localPort }
        require(port in 0..65535)
        val server = LanSyncSecurity.context(identity).serverSocketFactory.createServerSocket() as SSLServerSocket
        server.enabledProtocols = server.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        server.needClientAuth = false // Per-peer credential authenticates clients inside pinned TLS.
        server.reuseAddress = true
        val portFile = File(privateDirectory, "listen-port")
        val desiredPort = if (port == 0 && portFile.exists()) {
            require(portFile.length() <= 5)
            LanSyncSecurity.readPrivate(portFile, 5).toInt().also { require(it in 1..65535) }
        } else port
        try {
            server.bind(InetSocketAddress(desiredPort), 4)
            LanSyncSecurity.writePrivate(portFile, server.localPort.toString())
        } catch (failure: Exception) { server.close(); throw failure }
        listener = server
        peerResults.clear()
        lastSuccessAtMillis = null
        lastError = null
        running = true
        val session = ++generation
        workers.submit { serve(server, session) }
        workers.submit { poll(session) }
        publish()
        server.localPort
    }

    fun createInvitation(host: String): String = synchronized(gate) {
        check(running && !closed && peers.size < MAX_PEERS)
        LanSyncSecurity.address(host)
        val invitation = PairingInvitation(host = host, port = listener!!.localPort,
            certificate = identity.certificatePem(), binding = binding,
            secret = LanSyncSecurity.secret(), expiresAtMillis = System.currentTimeMillis() + INVITATION_MS,
            deviceName = sanitizeDeviceName(localDeviceName()))
        pending = Pending(invitation)
        json.encodeToString(invitation)
    }

    /** Explicit pairing action only; caller adopts binding first, only with no existing peers. */
    suspend fun pair(invitation: String) {
        val invite = parseInvitation(invitation)
        val session: Long
        val ownPort: Int
        synchronized(gate) {
            check(running && !closed && peers.size < MAX_PEERS)
            require(invite.binding == binding && invite.expiresAtMillis > System.currentTimeMillis())
            require(peers.none { it.id == LanSyncSecurity.id(LanSyncSecurity.certificate(invite.certificate)) })
            session = generation
            ownPort = listener!!.localPort
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pairingContext = kotlin.coroutines.coroutineContext
            pairingContext.ensureActive()
            connect(invite.host, invite.port, invite.certificate) { socket ->
                val challenge = readText(socket, 128)
                pairingContext.ensureActive()
                write(socket, Wire(operation = "pair", binding = binding, id = deviceId,
                    credential = invite.secret, challenge = challenge, certificate = identity.certificatePem(),
                    port = ownPort, deviceName = sanitizeDeviceName(localDeviceName())))
                val response = read(socket)
                require(response.version == 1 && response.operation == "paired" && response.binding == binding && response.challenge == challenge)
                val peerName = sanitizeDeviceName(response.deviceName.ifBlank { invite.deviceName })
                val peer = Peer(response.id, invite.host, invite.port, invite.certificate, response.credential, peerName)
                validatePeer(peer)
                pairingContext.ensureActive()
                synchronized(gate) {
                    checkActive(session)
                    require(peers.size < MAX_PEERS && peers.none { it.id == peer.id })
                    save(peers + peer)
                    peers += peer
                }
                publish()
            }
        }
    }

    /** Returns only after any already-entered import callback has completed/cancelled. */
    @Synchronized fun stop() {
        synchronized(gate) {
            running = false
            generation++
            pending = null
            listener?.close()
            listener = null
            sockets.forEach { runCatching { it.close() } }
            jobs.forEach { it.cancel() }
        }
        synchronized(callbackGate) { /* barrier against post-stop imports */ }
        publish()
    }

    /** Call stop before history reset; this removes all retained peer authorizations. */
    @Synchronized fun unpairAll() {
        stop()
        synchronized(gate) { save(emptyList()); peers.clear(); peerResults.clear(); lastSuccessAtMillis = null; lastError = null }
        publish()
    }

    /** Revoke only after cancellation and the import barrier; retain other authorizations. */
    @Synchronized fun revokePeer(id: String) {
        val resume = synchronized(gate) {
            check(!closed)
            require(peers.any { it.id == id }) { "Unknown device" }
            running
        }
        stop()
        synchronized(gate) {
            val retained = peers.filterNot { it.id == id }
            save(retained) // Persistence failure leaves transport stopped, never silently resumed.
            peers.clear()
            peers.addAll(retained)
            peerResults.remove(id)
            lastSuccessAtMillis = null
            lastError = null
        }
        if (resume) start() else publish()
    }

    @Synchronized override fun close() {
        stop()
        synchronized(gate) { closed = true }
        workers.shutdownNow()
        timer.shutdownNow()
    }

    private fun serve(server: SSLServerSocket, session: Long) {
        while (active(session)) {
            try {
                val socket = server.accept() as SSLSocket
                // Sequential handling caps concurrency and queued work. Absolute timeout includes handshake.
                useSocket(socket) {
                    val host = socket.inetAddress.hostAddress ?: error("No peer address")
                    LanSyncSecurity.address(host)
                    socket.startHandshake()
                    val challenge = LanSyncSecurity.secret()
                    writeText(socket, challenge)
                    val request = read(socket)
                    require(request.version == 1 && request.binding == binding && request.challenge == challenge)
                    when (request.operation) {
                        "pair" -> acceptPair(socket, host, request, session)
                        "exchange" -> acceptExchange(socket, request, session)
                        else -> error("Unknown operation")
                    }
                }
            } catch (failure: Exception) {
                val changed = synchronized(gate) {
                    if (running && generation == session) { lastError = errorCode(failure); true } else false
                }
                if (changed) publish()
            }
        }
    }

    private fun acceptPair(socket: SSLSocket, host: String, request: Wire, session: Long) {
        synchronized(gate) {
            checkActive(session)
            val invitation = pending ?: error("Pairing closed")
            invitation.attempts++
            if (invitation.attempts > MAX_PAIR_ATTEMPTS || System.nanoTime() >= invitation.deadlineNanos || invitation.invitation.expiresAtMillis <= System.currentTimeMillis()) {
                pending = null
                error("Pairing expired")
            }
            require(LanSyncSecurity.equal(invitation.invitation.secret, request.credential))
            val peer = Peer(
                request.id, host, request.port, request.certificate, LanSyncSecurity.secret(),
                sanitizeDeviceName(request.deviceName)
            )
            validatePeer(peer)
            require(peer.id != deviceId && peers.size < MAX_PEERS && peers.none { it.id == peer.id })
            // Consume before persistence; failed persistence cannot leave a reusable invitation.
            pending = null
            save(peers + peer)
            peers += peer
            write(socket, Wire(operation = "paired", binding = binding, id = deviceId,
                credential = peer.credential, challenge = request.challenge,
                deviceName = sanitizeDeviceName(localDeviceName())))
        }
        publish()
    }

    private fun acceptExchange(socket: SSLSocket, request: Wire, session: Long) {
        val peer = synchronized(gate) {
            checkActive(session)
            peers.singleOrNull { it.id == request.id && LanSyncSecurity.equal(it.credential, request.credential) }
                ?: error("Unpaired device")
        }
        try {
            val payload = decodePayload(request.payload)
            callback(session) { callbacks.receiveFrom(peer.id, payload) }
            val response = callback(session) { callbacks.snapshotFor(peer.id) }
            synchronized(gate) {
                checkActive(session)
                require(peers.contains(peer))
                write(socket, Wire(operation = "exchanged", binding = binding, id = deviceId,
                    credential = "", challenge = request.challenge, payload = encodePayload(response)))
            }
            val acknowledgment = read(socket)
            require(acknowledgment.version == 1 && acknowledgment.operation == "received" &&
                acknowledgment.binding == binding && acknowledgment.id == peer.id &&
                acknowledgment.challenge == request.challenge && LanSyncSecurity.equal(acknowledgment.credential, peer.credential))
            recordResult(peer, session, null)
        } catch (failure: Exception) {
            recordResult(peer, session, errorCode(failure))
            throw failure
        }
        publish()
    }

    private fun poll(session: Long) {
        var failures = 0
        while (active(session)) {
            val current = synchronized(gate) { peers.toList() }
            var failed = false
            current.forEach { peer ->
                if (active(session)) try {
                    val payload = callback(session) { callbacks.snapshotFor(peer.id) }
                    connect(peer.host, peer.port, peer.certificate) { socket ->
                        val challenge = readText(socket, 128)
                        write(socket, Wire(operation = "exchange", binding = binding, id = deviceId,
                            credential = peer.credential, challenge = challenge, payload = encodePayload(payload)))
                        val response = read(socket)
                        require(response.version == 1 && response.operation == "exchanged" && response.binding == binding &&
                            response.id == peer.id && response.challenge == challenge)
                        callback(session) { callbacks.receiveFrom(peer.id, decodePayload(response.payload)) }
                        synchronized(gate) {
                            checkActive(session)
                            write(socket, Wire(operation = "received", binding = binding, id = deviceId,
                                credential = peer.credential, challenge = challenge))
                        }
                    }
                    recordResult(peer, session, null)
                } catch (failure: Exception) { failed = true; recordResult(peer, session, errorCode(failure)) }
            }
            if (current.isNotEmpty()) publish()
            failures = if (failed) (failures + 1).coerceAtMost(4) else 0
            val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos((intervalMillis * (1L shl failures)).coerceAtMost(120_000))
            try {
                while (active(session) && System.nanoTime() < until) Thread.sleep(100)
            } catch (_: InterruptedException) { return }
        }
    }

    private fun recordResult(peer: Peer, session: Long, error: String?) = synchronized(gate) {
        if (running && generation == session && peers.contains(peer)) {
            val success = if (error == null) System.currentTimeMillis() else peerResults[peer.id]?.first
            peerResults[peer.id] = success to error
            lastError = error
            if (error == null) lastSuccessAtMillis = success
        }
    }

    private fun <T> callback(session: Long, action: suspend () -> T): T = synchronized(callbackGate) {
        val job = Job()
        synchronized(gate) { checkActive(session); jobs += job }
        try { runBlocking(job) { kotlinx.coroutines.withTimeout(DEADLINE_MS) { action() } } } finally { jobs -= job; job.cancel() }
    }

    private fun <T> connect(host: String, port: Int, certificate: String, action: (SSLSocket) -> T): T {
        val socket = LanSyncSecurity.context(identity, LanSyncSecurity.certificate(certificate)).socketFactory.createSocket() as SSLSocket
        return useSocket(socket) {
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
            socket.connect(InetSocketAddress(LanSyncSecurity.address(host), port), SOCKET_MS)
            socket.startHandshake()
            action(socket)
        }
    }

    private fun <T> useSocket(socket: SSLSocket, action: () -> T): T {
        socket.soTimeout = SOCKET_MS
        synchronized(gate) {
            if (!running || closed) { socket.close(); error("Transport stopped") }
            sockets += socket
        }
        val deadline: ScheduledFuture<*> = timer.schedule({ runCatching { socket.close() } }, DEADLINE_MS, TimeUnit.MILLISECONDS)
        try { return socket.use { action() } } finally { deadline.cancel(false); sockets -= socket }
    }

    private fun read(socket: Socket): Wire = json.decodeFromString(readText(socket, MAX_FRAME))
    private fun write(socket: Socket, value: Wire) = writeText(socket, json.encodeToString(value))
    private fun readText(socket: Socket, max: Int): String {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        require(size in 1..max)
        return ByteArray(size).also(input::readFully).toString(Charsets.UTF_8)
    }
    private fun writeText(socket: Socket, value: String) {
        val bytes = value.toByteArray()
        require(bytes.size in 1..MAX_FRAME)
        DataOutputStream(socket.getOutputStream()).apply { writeInt(bytes.size); write(bytes); flush() }
    }
    private fun encodePayload(payload: ByteArray): String {
        if (payload.size > MAX_PAYLOAD) throw PayloadTooLargeException()
        return Base64.getEncoder().encodeToString(payload)
    }
    private fun decodePayload(payload: String): ByteArray {
        if (payload.length > (MAX_PAYLOAD + 2) / 3 * 4) throw PayloadTooLargeException()
        return Base64.getDecoder().decode(payload).also { if (it.size > MAX_PAYLOAD) throw PayloadTooLargeException() }
    }
    private fun validatePeer(peer: Peer) {
        LanSyncSecurity.address(peer.host)
        require(peer.port in 1..65535 && peer.credential.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(peer.id == LanSyncSecurity.id(LanSyncSecurity.certificate(peer.certificate)))
    }
    private fun errorCode(failure: Exception): String = when (failure) {
        is UnsupportedSyncProtocolException -> "unsupported_sync_protocol"
        is dev.ikna.data.export.JournalReconciliationRequiredException -> "journal_reconciliation_required"
        is dev.ikna.data.export.IncrementalJournalLimitException -> "journal_item_too_large"
        is PayloadTooLargeException, is dev.ikna.data.export.ProfileTransferLimitException -> "payload_too_large"
        else -> "connection_failed"
    }
    private fun save(values: List<Peer>) = LanSyncSecurity.writePrivate(File(privateDirectory, "peers.json"), json.encodeToString(Saved(binding = binding, peers = values)))
    private fun active(session: Long): Boolean = synchronized(gate) { running && generation == session }
    private fun checkActive(session: Long) { check(running && generation == session && !closed) }
    private fun publish() { runCatching { onStatus(status) } }

    companion object {
        const val MAX_PAYLOAD = 16 * 1024 * 1024
        const val MAX_FRAME = 24 * 1024 * 1024
        const val MAX_PEERS = 4
        const val COMPACT_INVITATION_PREFIX = "ikna-pair:1:"
        private const val SOCKET_MS = 5_000
        private const val DEADLINE_MS = 30_000L
        private const val INVITATION_MS = 300_000L
        private const val MAX_PAIR_ATTEMPTS = 8
        private const val MAX_INVITATION_JSON_BYTES = 8192

        fun sanitizeDeviceName(raw: String): String =
            raw.trim().replace(Regex("[\\r\\n\\t]+"), " ").take(48)

        fun defaultDeviceDisplayName(): String {
            val androidModel = runCatching {
                val build = Class.forName("android.os.Build")
                val manufacturer = (build.getField("MANUFACTURER").get(null) as? String).orEmpty().trim()
                val model = (build.getField("MODEL").get(null) as? String).orEmpty().trim()
                when {
                    model.isEmpty() -> ""
                    manufacturer.isNotEmpty() && !model.startsWith(manufacturer, ignoreCase = true) ->
                        "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
                    else -> model
                }
            }.getOrDefault("")
            if (androidModel.isNotBlank()) return sanitizeDeviceName(androidModel)
            val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("").trim()
            val os = System.getProperty("os.name").orEmpty().trim()
            return sanitizeDeviceName(
                when {
                    host.isNotBlank() && os.isNotBlank() -> "$host ($os)"
                    host.isNotBlank() -> host
                    os.isNotBlank() -> os
                    else -> "Ikna"
                }
            )
        }

        /** Compresses a JSON invitation into a shorter QR-friendly string without losing compatibility. */
        fun encodeCompactInvitation(rawJson: String): String {
            val input = rawJson.toByteArray(Charsets.UTF_8)
            require(input.size in 1..MAX_INVITATION_JSON_BYTES)
            val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
            return try {
                deflater.setInput(input)
                deflater.finish()
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1024)
                while (!deflater.finished()) {
                    val count = deflater.deflate(buf)
                    if (count <= 0) break
                    out.write(buf, 0, count)
                }
                COMPACT_INVITATION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
            } finally {
                deflater.end()
            }
        }

        private fun decodeCompactInvitation(compact: String): String {
            val encoded = compact.removePrefix(COMPACT_INVITATION_PREFIX).trim()
            require(encoded.isNotEmpty() && encoded.length <= MAX_INVITATION_JSON_BYTES)
            val compressed = Base64.getUrlDecoder().decode(encoded)
            val inflater = java.util.zip.Inflater()
            return try {
                inflater.setInput(compressed)
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1024)
                var total = 0
                while (!inflater.finished()) {
                    val count = inflater.inflate(buf)
                    if (count == 0) {
                        if (inflater.needsInput() || inflater.needsDictionary()) break
                    } else {
                        total += count
                        require(total <= MAX_INVITATION_JSON_BYTES)
                        out.write(buf, 0, count)
                    }
                }
                out.toString(Charsets.UTF_8.name())
            } finally {
                inflater.end()
            }
        }

        fun parseInvitation(text: String): PairingInvitation {
            val trimmed = text.trim()
            require(trimmed.length <= MAX_INVITATION_JSON_BYTES)
            val rawJson = if (trimmed.startsWith(COMPACT_INVITATION_PREFIX)) {
                decodeCompactInvitation(trimmed)
            } else {
                trimmed
            }
            require(rawJson.length <= MAX_INVITATION_JSON_BYTES)
            val value = Json.decodeFromString<PairingInvitation>(rawJson)
            require(value.version == 1 && value.port in 1..65535 && value.secret.matches(Regex("[A-Za-z0-9_-]{43}")))
            LanSyncSecurity.address(value.host)
            LanSyncSecurity.certificate(value.certificate).checkValidity()
            require(value.expiresAtMillis > System.currentTimeMillis() && value.expiresAtMillis <= System.currentTimeMillis() + INVITATION_MS + 30_000)
            return value
        }
        fun localAddresses(): List<String> {
            val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
            return ifaces
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(true) }
                .flatMap { Collections.list(it.inetAddresses) }
                .mapNotNull { it.hostAddress }
                .filter { !it.startsWith("127.") && runCatching { LanSyncSecurity.address(it) }.isSuccess }
                .distinct()
                .sortedWith(
                    compareBy<String>(
                        // Prefer typical home/office Wi-Fi subnets (192.168.x.x) over virtual/WSL/VPN bridges (172.x.x.x).
                        { ip ->
                            when {
                                ip.startsWith("192.168.") -> 0
                                ip.startsWith("10.") -> 1
                                ip.startsWith("172.") -> 2
                                else -> 3
                            }
                        },
                        { it }
                    )
                )
        }
    }
}
