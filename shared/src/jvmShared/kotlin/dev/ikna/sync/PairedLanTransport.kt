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
import java.net.Inet4Address
import java.net.InetAddress
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
    val version: Int = 1, val host: String, val port: Int, val certificate: String = "",
    val binding: HistoryBinding, val secret: String, val expiresAtMillis: Long,
    val deviceName: String = "",
    val hosts: List<String> = emptyList(),
    val certPin: String = "",
) {
    override fun toString(): String = "PairingInvitation(version=$version, accessGrant=<redacted>)"
}

/** Safe, allowlisted sync failure code without secrets or raw network/exception details. */
class SyncOperationException(val code: String) : IllegalStateException(code)

/** Foreground-only LAN exchange. All application data remains opaque to the transport. */
class PairedLanTransport(
    private val privateDirectory: File,
    val binding: HistoryBinding,
    private val callbacks: PayloadCallbacks,
    private val onStatus: (Status) -> Unit = {},
    private val intervalMillis: Long = 15_000,
    private val localDeviceName: () -> String = { defaultDeviceDisplayName() },
    private val directLanSocketFactory: ((target: InetAddress, port: Int, timeoutMs: Int) -> Socket?)? = null,
    private val onLog: (String, Boolean) -> Unit = { _, _ -> },
) : AutoCloseable {
    @Serializable enum class PeerRole { INVITER, JOINER, UNKNOWN }
    enum class ActivityStage {
        IDLE, PAIRING_CONNECTING, PAIRING_HANDSHAKE,
        CONNECTING_PEER, HANDSHAKING_PEER, EXCHANGING_DATA
    }
    data class Activity(
        val stage: ActivityStage = ActivityStage.IDLE,
        val attempt: Int = 0,
        val total: Int = 0,
    )
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
        val role: PeerRole = PeerRole.UNKNOWN,
    ) {
        override fun toString(): String = "PeerStatus(identity=<redacted>, address=<redacted>, lastSuccessAtMillis=$lastSuccessAtMillis, lastError=$lastError)"
    }
    data class Status(
        val running: Boolean, val peerCount: Int, val lastError: String?,
        val lastSuccessAtMillis: Long? = null, val peers: List<PeerStatus> = emptyList(),
        val activity: Activity = Activity(),
    )
    @Serializable private data class Peer(
        val id: String, val host: String, val port: Int, val certificate: String, val credential: String,
        val name: String = "",
        val hosts: List<String> = emptyList(),
        val role: PeerRole = PeerRole.UNKNOWN,
    )
    @Serializable private data class Saved(val version: Int = 1, val binding: HistoryBinding, val peers: List<Peer>)
    @Serializable private data class Wire(
        val version: Int = 1, val operation: String, val binding: HistoryBinding,
        val id: String, val credential: String, val challenge: String,
        val certificate: String = "", val port: Int = 0, val payload: String = "",
        val deviceName: String = "",
        val hosts: List<String> = emptyList(),
    )
    private class PayloadTooLargeException : IllegalArgumentException()
    private data class Pending(val invitation: PairingInvitation, val deadlineNanos: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(INVITATION_MS), var attempts: Int = 0)
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
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
    @Volatile private var syncNowRequested = false
    @Volatile private var activity: Activity = Activity()
    val status: Status get() = synchronized(gate) { Status(running, peers.size, lastError, lastSuccessAtMillis,
        peers.map { peer -> PeerStatus(peer.id, peer.id.chunked(4).joinToString(" "), "${peer.host}:${peer.port}",
            peerResults[peer.id]?.first, peerResults[peer.id]?.second, peer.name, peer.role) },
        activity) }

    private fun setActivity(stage: ActivityStage, attempt: Int = 0, total: Int = 0) {
        val next = Activity(stage, attempt, total)
        if (activity != next) {
            activity = next
            publish()
        }
    }

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
    @Synchronized fun start(port: Int = 0, bindAddress: InetAddress? = null): Int = synchronized(gate) {
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
            val endpoint = if (bindAddress != null) InetSocketAddress(bindAddress, desiredPort) else InetSocketAddress(desiredPort)
            server.bind(endpoint, 4)
            LanSyncSecurity.writePrivate(portFile, server.localPort.toString())
        } catch (failure: Exception) { server.close(); throw failure }
        listener = server
        peerResults.clear()
        lastSuccessAtMillis = null
        lastError = null
        activity = Activity()
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
        val fallbackHosts = if (host.startsWith("127.")) {
            emptyList()
        } else {
            (listOf(host) + localAddresses()).distinct().take(MAX_CANDIDATE_HOSTS)
        }
        val invitation = PairingInvitation(host = host, port = listener!!.localPort,
            certificate = identity.certificatePem(), binding = binding,
            secret = LanSyncSecurity.secret(), expiresAtMillis = System.currentTimeMillis() + INVITATION_MS,
            deviceName = sanitizeDeviceName(localDeviceName()),
            hosts = fallbackHosts)
        pending = Pending(invitation)
        json.encodeToString(invitation)
    }

    /** Wakes the background poll loop immediately and resets exponential backoff. */
    fun triggerSyncNow() {
        syncNowRequested = true
    }

    /** Explicit pairing action only; caller adopts binding first, only with no existing peers. */
    suspend fun pair(invitation: String) {
        val invite = parseInvitation(invitation)
        val expectedPin = invite.certPin.ifBlank { LanSyncSecurity.id(LanSyncSecurity.certificate(invite.certificate)) }
        val session: Long
        val ownPort: Int
        synchronized(gate) {
            check(running && !closed && peers.size < MAX_PEERS)
            if (invite.binding != binding) throw SyncOperationException("history-mismatch")
            if (invite.expiresAtMillis <= System.currentTimeMillis()) throw SyncOperationException("expired_invitation")
            if (peers.any { it.id == expectedPin }) {
                throw SyncOperationException("already_paired")
            }
            session = generation
            ownPort = listener!!.localPort
        }
        val candidates = (listOf(invite.host) + invite.hosts).distinct().take(MAX_CANDIDATE_HOSTS)
        val ownHosts = if (invite.host.startsWith("127.")) emptyList() else localAddresses().take(MAX_CANDIDATE_HOSTS)
        onLog("Pairing: dialing ${candidates.joinToString(", ")}:${invite.port}", false)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val pairingContext = kotlin.coroutines.coroutineContext
            pairingContext.ensureActive()
            try {
                connectAny(candidates, invite.port, invite.certificate, certPin = expectedPin, isPairing = true) { workingHost, socket ->
                    val serverCert = socket.session.peerCertificates.firstOrNull() as? java.security.cert.X509Certificate
                        ?: throw SyncOperationException("tls_trust_failed")
                    val verifiedCertPem = invite.certificate.ifBlank { LanSyncSecurity.encodeCertificatePem(serverCert) }
                    if (LanSyncSecurity.id(LanSyncSecurity.certificate(verifiedCertPem)) != expectedPin) {
                        throw SyncOperationException("tls_trust_failed")
                    }
                    onLog("TLS verified on $workingHost:${invite.port} (#${expectedPin.take(8)})", false)
                    val challenge = readText(socket, 128)
                    pairingContext.ensureActive()
                    write(socket, Wire(operation = "pair", binding = binding, id = deviceId,
                        credential = invite.secret, challenge = challenge, certificate = identity.certificatePem(),
                        port = ownPort,
                        deviceName = if (invite.deviceName.isNotBlank()) sanitizeDeviceName(localDeviceName()) else "",
                        hosts = ownHosts))
                    val response = read(socket)
                    if (response.version == 1 && response.operation == "error" && response.challenge == challenge) {
                        val remoteCode = sanitizeRemoteErrorCode(response.payload)
                        onLog("Pairing rejected by peer: $remoteCode", true)
                        throw SyncOperationException(remoteCode)
                    }
                    require(response.version == 1 && response.operation == "paired" && response.binding == binding && response.challenge == challenge)
                    val peerName = sanitizeDeviceName(response.deviceName.ifBlank { invite.deviceName })
                    val peerHosts = (listOf(workingHost) + candidates).distinct().take(MAX_CANDIDATE_HOSTS)
                    val peer = Peer(
                        response.id, workingHost, invite.port, verifiedCertPem, response.credential,
                        peerName, peerHosts, PeerRole.JOINER
                    )
                    validatePeer(peer)
                    pairingContext.ensureActive()
                    synchronized(gate) {
                        checkActive(session)
                        require(peers.size < MAX_PEERS && peers.none { it.id == peer.id })
                        save(peers + peer)
                        peers += peer
                    }
                    onLog("Paired as JOINER with ${peerName.ifBlank { workingHost }} ($workingHost:${invite.port})", false)
                    syncNowRequested = true
                    publish()
                }
            } finally {
                setActivity(ActivityStage.IDLE)
            }
        }
    }

    /** Returns only after any already-entered import callback has completed/cancelled. */
    @Synchronized fun stop() {
        synchronized(gate) {
            running = false
            generation++
            pending = null
            activity = Activity()
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
                    socket.soTimeout = EXCHANGE_SOCKET_MS
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
        fun sendPairError(code: String): Nothing {
            runCatching {
                write(socket, Wire(operation = "error", binding = binding, id = deviceId,
                    credential = "", challenge = request.challenge, payload = code))
            }
            throw SyncOperationException(code)
        }
        synchronized(gate) {
            checkActive(session)
            val invitation = pending ?: sendPairError("expired_invitation")
            invitation.attempts++
            if (invitation.attempts > MAX_PAIR_ATTEMPTS || System.nanoTime() >= invitation.deadlineNanos || invitation.invitation.expiresAtMillis <= System.currentTimeMillis()) {
                pending = null
                sendPairError("expired_invitation")
            }
            if (!LanSyncSecurity.equal(invitation.invitation.secret, request.credential)) {
                sendPairError("invalid_invitation")
            }
            val candidatePeerHosts = (listOf(host) + request.hosts)
                .filter { runCatching { LanSyncSecurity.address(it) }.isSuccess }
                .distinct()
                .take(MAX_CANDIDATE_HOSTS)
            val peer = Peer(
                request.id, host, request.port, request.certificate, LanSyncSecurity.secret(),
                sanitizeDeviceName(request.deviceName),
                candidatePeerHosts,
                PeerRole.INVITER
            )
            validatePeer(peer)
            require(peer.id != deviceId && peers.size < MAX_PEERS && peers.none { it.id == peer.id })
            // Consume before persistence; failed persistence cannot leave a reusable invitation.
            pending = null
            save(peers + peer)
            peers += peer
            write(socket, Wire(operation = "paired", binding = binding, id = deviceId,
                credential = peer.credential, challenge = request.challenge,
                deviceName = if (request.deviceName.isNotBlank()) sanitizeDeviceName(localDeviceName()) else ""))
            onLog("Paired as INVITER with ${peer.name.ifBlank { host }} ($host:${request.port})", false)
        }
        syncNowRequested = true
        publish()
    }

    private fun acceptExchange(socket: SSLSocket, request: Wire, session: Long) {
        val peer = synchronized(gate) {
            checkActive(session)
            peers.singleOrNull { it.id == request.id && LanSyncSecurity.equal(it.credential, request.credential) }
                ?: error("Unpaired device")
        }
        setActivity(ActivityStage.EXCHANGING_DATA)
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
            val code = errorCode(failure)
            onLog("Incoming exchange error: $code (${failure.javaClass.simpleName})", true)
            if (code in REMOTE_REPORTABLE_ERRORS) {
                runCatching {
                    write(socket, Wire(operation = "error", binding = binding, id = deviceId,
                        credential = "", challenge = request.challenge, payload = code))
                }
            }
            recordResult(peer, session, code)
            throw failure
        } finally {
            setActivity(ActivityStage.IDLE)
        }
        publish()
    }

    private fun poll(session: Long) {
        var failures = 0
        while (active(session)) {
            syncNowRequested = false
            val current = synchronized(gate) { peers.toList() }
            var failed = false
            current.forEach { peer ->
                if (active(session)) try {
                    val payload = callback(session) { callbacks.snapshotFor(peer.id) }
                    val candidates = (listOf(peer.host) + peer.hosts).distinct().take(MAX_CANDIDATE_HOSTS)
                    val workingHost = connectAny(candidates, peer.port, peer.certificate, certPin = peer.id, isPairing = false) { matchedHost, socket ->
                        setActivity(ActivityStage.EXCHANGING_DATA)
                        val challenge = readText(socket, 128)
                        write(socket, Wire(operation = "exchange", binding = binding, id = deviceId,
                            credential = peer.credential, challenge = challenge, payload = encodePayload(payload)))
                        val response = read(socket)
                        if (response.version == 1 && response.operation == "error" && response.challenge == challenge) {
                            val remoteCode = sanitizeRemoteErrorCode(response.payload)
                            onLog("Peer reported error: $remoteCode", true)
                            throw SyncOperationException(remoteCode)
                        }
                        require(response.version == 1 && response.operation == "exchanged" && response.binding == binding &&
                            response.id == peer.id && response.challenge == challenge)
                        callback(session) { callbacks.receiveFrom(peer.id, decodePayload(response.payload)) }
                        synchronized(gate) {
                            checkActive(session)
                            write(socket, Wire(operation = "received", binding = binding, id = deviceId,
                                credential = peer.credential, challenge = challenge))
                        }
                        matchedHost
                    }
                    if (workingHost != peer.host) {
                        promotePeerHost(peer.id, workingHost, session)
                    }
                    recordResult(peer, session, null)
                } catch (failure: Exception) {
                    failed = true
                    val code = errorCode(failure)
                    onLog("Outgoing exchange error (${peer.host}:${peer.port}): $code (${failure.javaClass.simpleName})", true)
                    recordResult(peer, session, code)
                }
                finally { setActivity(ActivityStage.IDLE) }
            }
            if (current.isNotEmpty()) publish()
            failures = if (failed) (failures + 1).coerceAtMost(4) else 0
            val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos((intervalMillis * (1L shl failures)).coerceAtMost(120_000))
            try {
                while (active(session) && System.nanoTime() < until) {
                    if (syncNowRequested) {
                        failures = 0
                        break
                    }
                    Thread.sleep(100)
                }
            } catch (_: InterruptedException) { return }
        }
    }

    private fun promotePeerHost(peerId: String, workingHost: String, session: Long) = synchronized(gate) {
        if (!running || generation != session) return@synchronized
        val index = peers.indexOfFirst { it.id == peerId }
        if (index < 0) return@synchronized
        val existing = peers[index]
        if (existing.host == workingHost) return@synchronized
        val reordered = (listOf(workingHost) + existing.hosts + existing.host).distinct().take(MAX_CANDIDATE_HOSTS)
        val updated = existing.copy(host = workingHost, hosts = reordered)
        val next = peers.toMutableList().apply { set(index, updated) }
        runCatching { save(next) }
        peers[index] = updated
    }

    private fun recordResult(peer: Peer, session: Long, error: String?) = synchronized(gate) {
        if (running && generation == session && peers.any { it.id == peer.id }) {
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

    private fun <T> connectAny(
        hosts: List<String>,
        port: Int,
        certificate: String,
        certPin: String = "",
        isPairing: Boolean = false,
        action: (String, SSLSocket) -> T
    ): T {
        val candidates = hosts.distinct().take(MAX_CANDIDATE_HOSTS)
        require(candidates.isNotEmpty())
        var lastFailure: Exception? = null
        val connectStage = if (isPairing) ActivityStage.PAIRING_CONNECTING else ActivityStage.CONNECTING_PEER
        val handshakeStage = if (isPairing) ActivityStage.PAIRING_HANDSHAKE else ActivityStage.HANDSHAKING_PEER
        for ((index, candidate) in candidates.withIndex()) {
            try {
                setActivity(connectStage, index + 1, candidates.size)
                return connect(candidate, port, certificate, certPin = certPin, onHandshake = {
                    setActivity(handshakeStage, index + 1, candidates.size)
                }) { socket -> action(candidate, socket) }
            } catch (failure: Exception) {
                lastFailure = failure
                // Protocol/auth/size errors are authoritative; only network/connect failures try fallback hosts.
                if (failure !is java.io.IOException) throw failure
            }
        }
        throw lastFailure ?: java.io.IOException("No reachable host")
    }

    private fun <T> connect(
        host: String,
        port: Int,
        certificate: String,
        certPin: String = "",
        onHandshake: () -> Unit = {},
        action: (SSLSocket) -> T
    ): T {
        val targetAddress = LanSyncSecurity.address(host)
        val pinnedCert = certificate.takeIf { it.isNotBlank() }?.let { LanSyncSecurity.certificate(it) }
        val sslFactory = LanSyncSecurity.context(
            identity,
            pin = pinnedCert,
            pinFingerprint = certPin.takeIf { it.isNotBlank() }
        ).socketFactory
        val directRaw = if (LanSyncSecurity.isDirectLanAddress(targetAddress)) {
            openDirectLanSocket(targetAddress, port, SOCKET_MS)
        } else {
            null
        }
        val socket = if (directRaw != null) {
            try {
                sslFactory.createSocket(directRaw, host, port, true) as SSLSocket
            } catch (failure: Exception) {
                runCatching { directRaw.close() }
                throw failure
            }
        } else {
            (sslFactory.createSocket() as SSLSocket).also { rawSsl ->
                try {
                    rawSsl.connect(InetSocketAddress(targetAddress, port), SOCKET_MS)
                } catch (failure: Exception) {
                    runCatching { rawSsl.close() }
                    throw failure
                }
            }
        }
        return useSocket(socket) {
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
            onHandshake()
            socket.startHandshake()
            socket.soTimeout = EXCHANGE_SOCKET_MS
            action(socket)
        }
    }

    /**
     * Attempts a direct TCP connect bound to the physical LAN interface before falling back to
     * system default routing. This bypasses full-tunnel VPNs on both Android (via ConnectivityManager
     * non-VPN Wi-Fi/Ethernet Network.socketFactory) and Desktop (via same-subnet physical NIC bind).
     */
    private fun openDirectLanSocket(target: InetAddress, port: Int, timeoutMs: Int): Socket? {
        directLanSocketFactory?.let { factory ->
            val platformSocket = runCatching { factory(target, port, timeoutMs) }.getOrNull()
            if (platformSocket != null) return platformSocket
        }
        val localLan = findMatchingPhysicalLanAddress(target) ?: return null
        val raw = Socket()
        return try {
            raw.bind(InetSocketAddress(localLan, 0))
            raw.connect(InetSocketAddress(target, port), timeoutMs)
            raw
        } catch (_: Exception) {
            runCatching { raw.close() }
            null
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
        require(peer.hosts.size <= MAX_CANDIDATE_HOSTS)
        peer.hosts.forEach { LanSyncSecurity.address(it) }
        require(peer.port in 1..65535 && peer.credential.matches(Regex("[A-Za-z0-9_-]{43}")))
        require(peer.id == LanSyncSecurity.id(LanSyncSecurity.certificate(peer.certificate)))
    }
    private fun errorCode(failure: Exception): String = when {
        failure is SyncOperationException -> failure.code
        failure is UnsupportedSyncProtocolException -> "unsupported_sync_protocol"
        failure is dev.ikna.data.export.JournalReconciliationRequiredException -> "journal_reconciliation_required"
        failure is dev.ikna.data.export.IncrementalJournalLimitException -> "journal_item_too_large"
        failure is PayloadTooLargeException || failure is dev.ikna.data.export.ProfileTransferLimitException -> "payload_too_large"
        failure is java.net.SocketTimeoutException -> "connection_timeout"
        failure is java.net.ConnectException -> "connection_refused"
        failure is java.net.NoRouteToHostException || failure is java.net.UnknownHostException -> "network_unreachable"
        failure is javax.net.ssl.SSLException -> "tls_trust_failed"
        failure is java.io.EOFException -> "peer_closed_connection"
        failure is java.net.BindException -> "port_unavailable"
        failure is java.net.SocketException -> "connection_interrupted"
        failure is java.io.IOException -> "connection_failed"
        failure.message == "Unpaired device" -> "unpaired_device"
        failure.message == "Pairing expired" || failure.message == "Pairing closed" -> "expired_invitation"
        failure.message in setOf("profile-conflict-or-stale", "profile-content-conflict") -> "profile-conflict-or-stale"
        failure.message == "proposal-expired" -> "proposal-expired"
        failure.message in setOf("local-profile-stale", "remote-profile-stale", "stale-proposal", "proposal-cancelled") -> "profile_stale"
        failure.message in setOf("history-mismatch", "fresh-history-reset-required") -> "history-mismatch"
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
        const val MAX_CANDIDATE_HOSTS = 6
        const val COMPACT_INVITATION_PREFIX = "ikna-pair:1:"
        const val COMPACT_QR_PREFIX = "ikna-pair:2:"
        private const val SOCKET_MS = 5_000
        private const val EXCHANGE_SOCKET_MS = 30_000
        private const val DEADLINE_MS = 60_000L
        private const val INVITATION_MS = 300_000L
        private const val CLOCK_SKEW_MILLIS = 300_000L
        private const val MAX_PAIR_ATTEMPTS = 8
        private const val MAX_INVITATION_JSON_BYTES = 8192
        private val HEX_64 = Regex("[a-f0-9]{64}")
        private val invitationJson = Json { ignoreUnknownKeys = true; isLenient = false }
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val compactV2Json = Json { ignoreUnknownKeys = true; isLenient = false; encodeDefaults = false; explicitNulls = false }

        @Serializable
        private data class CompactQrV2(
            val v: Int = 2,
            val h: String,
            val p: Int,
            val f: String,
            val g: String,
            val e: String,
            val r: Boolean = false,
            val s: String,
            val x: Long,
            val n: String = "",
            val a: List<String> = emptyList(),
        )

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

        /** Compresses a JSON invitation into a shorter v1 string (retaining full PEM for legacy peers). */
        fun encodeCompactInvitation(rawJson: String): String {
            return deflateWithPrefix(rawJson, COMPACT_INVITATION_PREFIX)
        }

        /**
         * Encodes an ultra-compact v2 QR invitation that replaces the ~700-byte PEM certificate
         * with its 32-byte SHA-256 fingerprint, reducing QR module density by ~4x for reliable
         * camera scanning from desktop displays while preserving identical TLS pinning security.
         */
        fun encodeQrInvitation(rawJson: String): String {
            val invite = invitationJson.decodeFromString<PairingInvitation>(rawJson)
            val pin = invite.certPin.ifBlank { LanSyncSecurity.id(LanSyncSecurity.certificate(invite.certificate)) }
            val extraHosts = invite.hosts.filter { it != invite.host }.distinct().take(2)
            val compact = CompactQrV2(
                h = invite.host,
                p = invite.port,
                f = pin,
                g = invite.binding.groupId,
                e = invite.binding.epoch,
                r = invite.binding.freshStart,
                s = invite.secret,
                x = invite.expiresAtMillis,
                n = sanitizeDeviceName(invite.deviceName).take(24),
                a = extraHosts,
            )
            return deflateWithPrefix(compactV2Json.encodeToString(compact), COMPACT_QR_PREFIX)
        }

        private fun deflateWithPrefix(text: String, prefix: String): String {
            val input = text.toByteArray(Charsets.UTF_8)
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
                prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
            } finally {
                deflater.end()
            }
        }

        private fun decodeDeflated(compact: String, prefix: String): String {
            val encoded = compact.removePrefix(prefix).trim()
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

        private val REMOTE_REPORTABLE_ERRORS = setOf(
            "expired_invitation", "invalid_invitation", "unsupported_sync_protocol",
            "payload_too_large", "journal_item_too_large", "journal_reconciliation_required",
            "profile-conflict-or-stale", "profile_stale", "proposal-expired", "history-mismatch"
        )

        private fun sanitizeRemoteErrorCode(raw: String): String =
            if (raw in REMOTE_REPORTABLE_ERRORS) raw else "connection_failed"

        fun parseInvitation(text: String): PairingInvitation {
            try {
                val trimmed = text.trim()
                require(trimmed.length <= MAX_INVITATION_JSON_BYTES)
                val value = if (trimmed.startsWith(COMPACT_QR_PREFIX)) {
                    val v2Json = decodeDeflated(trimmed, COMPACT_QR_PREFIX)
                    require(v2Json.length <= MAX_INVITATION_JSON_BYTES)
                    val v2 = compactV2Json.decodeFromString<CompactQrV2>(v2Json)
                    require(v2.v == 2 && v2.f.matches(HEX_64))
                    PairingInvitation(
                        version = 1,
                        host = v2.h,
                        port = v2.p,
                        certificate = "",
                        binding = HistoryBinding(v2.g, v2.e, v2.r),
                        secret = v2.s,
                        expiresAtMillis = v2.x,
                        deviceName = sanitizeDeviceName(v2.n),
                        hosts = (listOf(v2.h) + v2.a).distinct().take(MAX_CANDIDATE_HOSTS),
                        certPin = v2.f,
                    )
                } else {
                    val rawJson = if (trimmed.startsWith(COMPACT_INVITATION_PREFIX)) {
                        decodeDeflated(trimmed, COMPACT_INVITATION_PREFIX)
                    } else {
                        trimmed
                    }
                    require(rawJson.length <= MAX_INVITATION_JSON_BYTES)
                    invitationJson.decodeFromString<PairingInvitation>(rawJson)
                }
                require(value.version == 1 && value.port in 1..65535 && value.secret.matches(Regex("[A-Za-z0-9_-]{43}")))
                LanSyncSecurity.address(value.host)
                require(value.hosts.size <= MAX_CANDIDATE_HOSTS)
                value.hosts.forEach { LanSyncSecurity.address(it) }
                if (value.certificate.isNotBlank()) {
                    LanSyncSecurity.certificate(value.certificate).checkValidity()
                    if (value.certPin.isNotBlank()) {
                        require(value.certPin == LanSyncSecurity.id(LanSyncSecurity.certificate(value.certificate)))
                    }
                } else {
                    require(value.certPin.matches(HEX_64))
                }
                val nowMs = System.currentTimeMillis()
                if (value.expiresAtMillis <= nowMs) {
                    throw SyncOperationException("expired_invitation")
                }
                require(value.expiresAtMillis <= nowMs + INVITATION_MS + CLOCK_SKEW_MILLIS)
                return value
            } catch (already: SyncOperationException) {
                throw already
            } catch (_: Exception) {
                throw SyncOperationException("invalid_invitation")
            }
        }

        private val VPN_OR_VIRTUAL_TOKENS = listOf(
            "tun", "tap", "wg", "wintun", "wireguard", "tailscale", "zerotier", "clash", "sing",
            "meta", "nekoray", "amnezia", "vethernet", "hyper-v", "docker", "wsl", "vmware",
            "vbox", "virtual", "nordlynx", "proton", "mullvad", "outline", "utun", "ppp",
            "dummy", "bridge", "vpn", "radmin", "hamachi", "xray", "v2ray", "mihomo"
        )

        internal fun isLikelyVpnOrVirtualInterface(name: String, displayName: String): Boolean {
            val combined = "${name.lowercase()} ${displayName.lowercase()}"
            return VPN_OR_VIRTUAL_TOKENS.any { token -> combined.contains(token) }
        }

        internal fun rankCandidateAddress(ip: String, isVpnOrVirtual: Boolean): Int {
            val isOverlayCgnat = ip.startsWith("100.")
            return when {
                !isVpnOrVirtual && ip.startsWith("192.168.") -> 0
                !isVpnOrVirtual && ip.startsWith("10.") -> 1
                !isVpnOrVirtual && ip.startsWith("172.") -> 2
                !isVpnOrVirtual && ip.startsWith("169.254.") -> 3
                isOverlayCgnat -> 4
                ip.startsWith("192.168.") -> 5
                ip.startsWith("10.") -> 6
                ip.startsWith("172.") -> 7
                else -> 8
            }
        }

        private fun findMatchingPhysicalLanAddress(target: InetAddress): Inet4Address? {
            val targetBytes = (target as? Inet4Address)?.address ?: return null
            val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
            var bestMatch: Pair<Int, Inet4Address>? = null
            for (iface in ifaces) {
                val up = runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false)
                if (!up) continue
                if (isLikelyVpnOrVirtualInterface(iface.name.orEmpty(), iface.displayName.orEmpty())) continue
                for (ifAddr in iface.interfaceAddresses) {
                    val addr = ifAddr.address as? Inet4Address ?: continue
                    val localBytes = addr.address
                    val prefix = ifAddr.networkPrefixLength.toInt().coerceIn(8, 30)
                    if (sameIpv4Subnet(localBytes, targetBytes, prefix)) {
                        if (bestMatch == null || prefix > bestMatch.first) {
                            bestMatch = prefix to addr
                        }
                    }
                }
            }
            return bestMatch?.second
        }

        internal fun sameIpv4Subnet(a: ByteArray, b: ByteArray, prefixBits: Int): Boolean {
            if (a.size != 4 || b.size != 4 || prefixBits !in 1..32) return false
            val mask = if (prefixBits == 32) -1 else (-1 shl (32 - prefixBits))
            val ai = ((a[0].toInt() and 0xFF) shl 24) or ((a[1].toInt() and 0xFF) shl 16) or
                ((a[2].toInt() and 0xFF) shl 8) or (a[3].toInt() and 0xFF)
            val bi = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
            return (ai and mask) == (bi and mask)
        }

        fun localAddresses(): List<String> {
            val ifaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrDefault(emptyList())
            val ranked = mutableListOf<Pair<Int, String>>()
            for (iface in ifaces) {
                if (!runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(true)) continue
                val vpnOrVirtual = isLikelyVpnOrVirtualInterface(iface.name.orEmpty(), iface.displayName.orEmpty())
                for (addr in Collections.list(iface.inetAddresses)) {
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("127.") || runCatching { LanSyncSecurity.address(ip) }.isFailure) continue
                    ranked += rankCandidateAddress(ip, vpnOrVirtual) to ip
                }
            }
            return ranked
                .sortedWith(compareBy<Pair<Int, String>>({ it.first }, { it.second }))
                .map { it.second }
                .distinct()
        }
    }
}
