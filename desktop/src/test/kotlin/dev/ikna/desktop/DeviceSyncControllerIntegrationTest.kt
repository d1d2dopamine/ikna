package dev.ikna.desktop

import androidx.room.useReaderConnection
import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.ProfileTransferCodec
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.ChunkSelector
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.DeviceSyncController
import dev.ikna.sync.PairedLanTransport
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.ikna.domain.session.CommittedSessionChanges
import org.junit.Assert.*
import org.junit.Test

/** Two real disposable Room stores/controllers over pinned-TLS same-JVM loopback.
 * This is not physical-device or Android lifecycle evidence. Technical sentinels are not study content. */
class DeviceSyncControllerIntegrationTest {
    private class Device(val home: File, val replayGate: CompletableDeferred<Unit>? = null) {
        var db = openIknaDatabase(home.resolve("learner.db"))
        lateinit var repository: RestoreRepository
        lateinit var controller: DeviceSyncController
        val merged = AtomicInteger()
        val sessionChanges = CommittedSessionChanges()
        private val learningGuard = Mutex()
        val replayRelease = CompletableDeferred<Unit>()
        @Volatile var replayArmed = false

        init { wire() }

        private fun wire() {
            val realCards = db.cardDao()
            val cards = if (replayGate == null) realCards else object : CardDao by realCards {
                override suspend fun upsertAll(cards: List<CardEntity>) {
                    if (!replayArmed) { realCards.upsertAll(cards); return }
                    replayGate.complete(Unit)
                    // Room owns its transaction coroutine; release the DAO operation explicitly.
                    // The repository must then notice originating-call cancellation before commit.
                    replayRelease.await()
                    realCards.upsertAll(cards)
                }
            }
            repository = RestoreRepository(db, cards, db.reviewDao(), db.statsDao(), db.planDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
                Scheduler(dayStartHour = 4), GovernorConfig(),
                beforeExternalRestore = { controller.beforeExternalRestore() },
                afterExternalRestore = { controller.afterExternalRestore(it) })
            controller = DeviceSyncController(home.resolve("sync"), repository,
                onMerged = {
                    check(learningGuard.tryLock()) { "Post-commit callback reentered the learning guard" }
                    learningGuard.unlock()
                    merged.incrementAndGet()
                    sessionChanges.notifyCommitted()
                }, intervalMillis = 100)
            repository.historyBoundary = { controller.historyBoundary() }
            repository.journalMergeGuard = { block -> learningGuard.withLock { block() } }
        }

        suspend fun seed(target: String = TARGET, pack: String = PACK,
                         translation: String = "__TECHNICAL_FIXTURE__") {
            db.chunkDao().upsertPack(PackEntity(pack, 1, "en", 1, TS, title = "__SAME_FIXTURE_TITLE__"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(target, pack, "en",
                "__SYNC_TEST_SENTINEL__", "__SYNC_TEST_SENTINEL__", translation, 0, 1, 1)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(pack, target, 1)))
        }

        suspend fun enable() {
            controller.setForeground(true)
            assertFalse(controller.state.value.running)
            controller.setEnabled(true)
            assertTrue(controller.state.value.running)
        }

        suspend fun reviewCount(): Long = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM reviews") { statement ->
                check(statement.step())
                statement.getLong(0)
            }
        }

        suspend fun append(id: String, target: String = TARGET) {
            repository.mergeJournal(listOf(answer(id, target)), (controller.historyBoundary() ?: 0L), controller.boundaryToken())
        }

        suspend fun restart() {
            controller.close()
            db.close()
            db = openIknaDatabase(home.resolve("learner.db"))
            wire()
            controller.setForeground(true)
        }

        suspend fun reset() {
            val generation = controller.beforeHistoryChange()
            val boundary = LearningRepository(db, db.cardDao(), db.chunkDao(), db.reviewDao(),
                db.statsDao(), db.governorDao(), db.planDao(), db.browseDao(),
                ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db),
                Scheduler(dayStartHour = 4), ChunkSelector(), GovernorConfig()).resetProgressWithBoundary()
            controller.afterProgressReset(boundary, generation)
        }

        suspend fun close() { controller.close(); db.close() }
    }

    private suspend fun fixture(gate: CompletableDeferred<Unit>? = null, block: suspend (Device, Device) -> Unit) {
        val home = Files.createTempDirectory("ikna-controller-integration-").toFile()
        val a = Device(home.resolve("a").apply { mkdirs() }, gate)
        val b = Device(home.resolve("b").apply { mkdirs() })
        try { a.seed(); b.seed(); block(a, b) }
        finally {
            a.replayRelease.complete(Unit); b.replayRelease.complete(Unit)
            try { a.close() } finally { b.close(); home.deleteRecursively() }
        }
    }

    private suspend fun eventually(message: String, condition: suspend () -> Boolean) {
        try { withTimeout(15_000) { while (!condition()) delay(25) } }
        catch (timeout: TimeoutCancellationException) { throw AssertionError(message, timeout) }
    }

    private suspend fun pair(a: Device, b: Device, consent: Boolean = true): String {
        a.controller.createInvitation("127.0.0.1")
        val invitation = requireNotNull(a.controller.state.value.invitation)
        b.controller.pair(invitation)
        assertNull(b.controller.state.value.error)
        eventually("Both devices must retain the explicitly paired peer") {
            a.controller.state.value.peers == 1 && b.controller.state.value.peers == 1
        }
        if (consent) approveMerge(a, b)
        return invitation
    }

    private suspend fun propose(a: Device, b: Device): String {
        eventually("Both authenticated profiles must be available for a read-only proposal") {
            if (a.controller.state.value.profileConsent.operationId == null) {
                a.controller.proposeMerge(a.controller.state.value.devices.single().id)
            }
            a.controller.state.value.profileConsent.operationId != null
        }
        val operation = requireNotNull(a.controller.state.value.profileConsent.operationId)
        eventually("Peer must see the exact same proposed operation") {
            b.controller.state.value.profileConsent.operationId == operation
        }
        return operation
    }

    private suspend fun approveMerge(a: Device, b: Device) {
        eventually("Inviter must receive the peer profile before making a one-click choice") {
            a.controller.state.value.profileConsent.preview != null
        }
        a.controller.chooseMerge(a.controller.state.value.devices.single().id)
        val operation = requireNotNull(a.controller.state.value.profileConsent.operationId)
        try {
            eventually("Inviter choice must auto-apply on joiner and complete durable receipts") {
                a.controller.state.value.profileConsent.complete && b.controller.state.value.profileConsent.complete
            }
        } catch (failure: AssertionError) {
            throw AssertionError("Consent did not finish: a=${a.controller.state.value} " +
                "${a.controller.state.value.profileConsent}; b=${b.controller.state.value} " +
                "${b.controller.state.value.profileConsent}; " +
                "aReceipt=${a.repository.profileTransfer.appliedOperation(operation)?.resultingProfileIdentity}; " +
                "bReceipt=${b.repository.profileTransfer.appliedOperation(operation)?.resultingProfileIdentity}; " +
                "aProfile=${ProfileTransferCodec.profileIdentity(a.repository.profileTransfer.snapshot())}; " +
                "bProfile=${ProfileTransferCodec.profileIdentity(b.repository.profileTransfer.snapshot())}", failure)
        }
    }

    @Test(timeout = 60_000) fun `approved profile pairing automatically converges and repeated polls are noops`() = runBlocking {
        fixture { a, b ->
            assertFalse(a.controller.state.value.enabled)
            a.enable(); b.enable(); pair(a, b)
            assertEquals(0L, a.sessionChanges.generation.value) // Empty consent only bootstrapped metadata.
            assertEquals(0L, b.sessionChanges.generation.value)
            a.append("technical-event-a"); b.append("technical-event-b")
            eventually("Independent local answers must converge without transfer calls") {
                a.repository.snapshotJournal().size == 2 && b.repository.snapshotJournal().size == 2
            }
            assertEquals(a.repository.snapshotJournal(), b.repository.snapshotJournal())
            assertEquals(a.db.cardDao().all(), b.db.cardDao().all())
            eventually("Both devices must report acknowledged success") {
                a.controller.state.value.lastSuccessAtMillis != null && b.controller.state.value.lastSuccessAtMillis != null
            }
            assertTrue(a.sessionChanges.generation.value > 0)
            assertTrue(b.sessionChanges.generation.value > 0)
            val signals = a.sessionChanges.generation.value to b.sessionChanges.generation.value
            val callbacks = a.merged.get() to b.merged.get()
            val lastSuccess = a.controller.state.value.lastSuccessAtMillis!!
            eventually("A later automatic no-op exchange must complete") {
                (a.controller.state.value.lastSuccessAtMillis ?: 0) > lastSuccess
            }
            assertEquals(callbacks, a.merged.get() to b.merged.get())
            assertEquals(signals, a.sessionChanges.generation.value to b.sessionChanges.generation.value)
            assertEquals(2, a.db.reviewDao().all().size)
            assertEquals(2, b.db.reviewDao().all().size)
        }
    }

    @Test(timeout = 60_000) fun `foreground pause offline writes and process restart reconnect automatically`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable(); pair(a, b)
            b.controller.setForeground(false)
            assertFalse(b.controller.state.value.running)
            a.append("offline-a"); b.append("offline-b")
            delay(350)
            assertEquals(1, a.db.reviewDao().all().size)
            assertEquals(1, b.db.reviewDao().all().size)
            b.restart() // Persisted identity, binding, endpoint and explicit opt-in; no re-pair.
            eventually("Restart must reconnect and converge both offline journals") {
                a.repository.snapshotJournal().size == 2 && b.repository.snapshotJournal().size == 2
            }
            assertEquals(a.repository.snapshotJournal(), b.repository.snapshotJournal())
            assertEquals(a.db.cardDao().all(), b.db.cardDao().all())
        }
    }

    @Test(timeout = 60_000) fun `pair and joiner approval cannot mutate inviter then inviter choice merges automatically`() = runBlocking {
        fixture { a, b ->
            a.seed("__ONLY_A__", "__PACK_A__"); b.seed("__ONLY_B__", "__PACK_B__")
            a.append("unique-a", "__ONLY_A__"); b.append("unique-b", "__ONLY_B__")
            val beforeA = a.repository.profileTransfer.snapshot()
            val beforeB = b.repository.profileTransfer.snapshot()
            a.enable(); b.enable(); pair(a, b, consent = false)
            delay(350)
            assertEquals(beforeA, a.repository.profileTransfer.snapshot())
            assertEquals(beforeB, b.repository.profileTransfer.snapshot())
            assertEquals(PairedLanTransport.PeerRole.INVITER, a.controller.state.value.peerRole)
            assertEquals(PairedLanTransport.PeerRole.JOINER, b.controller.state.value.peerRole)
            val operation = propose(a, b)
            assertEquals(1, a.controller.state.value.profileConsent.preview!!.addedPacks)
            assertEquals(1, b.controller.state.value.profileConsent.preview!!.addedReviews)
            a.controller.approveProfile("__WRONG_OPERATION__")
            assertEquals("stale-proposal", a.controller.state.value.error)
            assertFalse(a.controller.state.value.profileConsent.localApproved)
            // Joiner approving alone cannot force mutation on the inviter.
            b.controller.approveProfile(operation)
            eventually("Inviter must observe joiner approval without mutating") {
                a.controller.state.value.profileConsent.remoteApproved
            }
            assertEquals(beforeA, a.repository.profileTransfer.snapshot())
            assertEquals(beforeB, b.repository.profileTransfer.snapshot())
            assertNull(a.repository.profileTransfer.appliedOperation(operation))
            assertNull(b.repository.profileTransfer.appliedOperation(operation))
            // Once the inviter approves, both profiles converge automatically.
            a.controller.approveProfile(operation)
            eventually("Inviter approval must converge both profiles") {
                a.controller.state.value.profileConsent.complete && b.controller.state.value.profileConsent.complete
            }
            assertEquals(3, a.db.chunkDao().packs().size)
            assertEquals(3, b.db.chunkDao().packs().size)
            assertFalse(a.db.chunkDao().pack("__PACK_B__")!!.isActive)
            assertFalse(b.db.chunkDao().pack("__PACK_A__")!!.isActive)
            assertEquals(setOf("unique-a", "unique-b"), a.repository.snapshotJournal().map { it.eventId }.toSet())
            assertEquals(ProfileTransferCodec.profileIdentity(a.repository.profileTransfer.snapshot()),
                ProfileTransferCodec.profileIdentity(b.repository.profileTransfer.snapshot()))
        }
    }

    @Test(timeout = 60_000) fun `same target identity with changed text rejects entire profile without mutation`() = runBlocking {
        fixture { a, b ->
            // A distinct target has the same stable ID on both devices, with conflicting text.
            a.seed("__CONFLICT__", "__CONFLICT_PACK__", "__TEXT_A__")
            b.seed("__CONFLICT__", "__CONFLICT_PACK__", "__TEXT_B__")
            b.append("conflicting-profile-event", "__CONFLICT__")
            val beforeA = a.repository.profileTransfer.snapshot()
            val beforeB = b.repository.profileTransfer.snapshot()
            a.enable(); b.enable(); pair(a, b, consent = false)
            eventually("Conflicting profile must be rejected visibly") {
                a.controller.proposeMerge(a.controller.state.value.devices.single().id)
                a.controller.state.value.error == "profile-conflict-or-stale"
            }
            assertNull(a.controller.state.value.profileConsent.operationId)
            assertEquals(beforeA, a.repository.profileTransfer.snapshot())
            assertEquals(beforeB, b.repository.profileTransfer.snapshot())
        }
    }

    @Test(timeout = 60_000) fun `answer after preview invalidates approval without importing the other profile`() = runBlocking {
        fixture { a, b ->
            b.seed("__REMOTE_ONLY__", "__REMOTE_PACK__")
            b.append("remote-before-preview", "__REMOTE_ONLY__")
            a.enable(); b.enable(); pair(a, b, consent = false)
            val operation = propose(a, b)
            a.append("local-after-preview")
            val afterAnswer = a.repository.profileTransfer.snapshot()
            a.controller.approveProfile(operation)
            assertEquals("profile-conflict-or-stale", a.controller.state.value.error)
            assertFalse(a.controller.state.value.profileConsent.localApproved)
            b.controller.approveProfile(operation)
            delay(350)
            assertEquals(afterAnswer, a.repository.profileTransfer.snapshot())
            assertNull(a.repository.profileTransfer.appliedOperation(operation))
            assertNull(b.repository.profileTransfer.appliedOperation(operation))
            assertEquals(listOf("remote-before-preview"), b.repository.snapshotJournal().map { it.eventId })
        }
    }

    @Test(timeout = 60_000) fun `revoking a pending approval clears consent and cannot commit later`() = runBlocking {
        fixture { a, b ->
            b.append("unapproved-event")
            a.enable(); b.enable(); pair(a, b, consent = false)
            val operation = propose(a, b)
            b.controller.setForeground(false)
            a.controller.approveProfile(operation)
            a.controller.revokePeer(a.controller.state.value.devices.single().id)
            b.controller.setForeground(true)
            assertEquals(0, a.controller.state.value.peers)
            assertNull(a.controller.state.value.profileConsent.operationId)
            a.controller.approveProfile(operation)
            delay(350)
            assertTrue(a.repository.snapshotJournal().isEmpty())
            assertNull(a.repository.profileTransfer.appliedOperation(operation))
            assertNull(b.repository.profileTransfer.appliedOperation(operation))
            a.restart()
            assertNull(a.controller.state.value.profileConsent.operationId)
            assertEquals(0, a.controller.state.value.peers)
        }
    }

    @Test(timeout = 60_000) fun `restart discards pending approval and requires a fresh choice`() = runBlocking {
        fixture { a, b ->
            b.append("pending-before-restart")
            a.enable(); b.enable(); pair(a, b, consent = false)
            val old = propose(a, b)
            b.controller.setForeground(false)
            a.controller.approveProfile(old)
            a.restart()
            b.controller.setForeground(true)
            delay(350)
            assertTrue(a.repository.snapshotJournal().isEmpty())
            assertNull(a.repository.profileTransfer.appliedOperation(old))
            assertFalse(a.controller.state.value.profileConsent.localApproved)
            // Explicitly cancel the surviving peer's pending proposal before choosing again.
            a.controller.cancelProfile(); b.controller.cancelProfile()
            eventually("Inviter must see peer preview after restart") {
                a.controller.state.value.profileConsent.preview != null
            }
            a.controller.chooseMerge(a.controller.state.value.devices.single().id)
            val next = requireNotNull(a.controller.state.value.profileConsent.operationId)
            assertNotEquals(old, next)
            eventually("Fresh inviter choice must auto-apply and finish after pending restart") {
                a.controller.state.value.profileConsent.complete && b.controller.state.value.profileConsent.complete
            }
            assertEquals(listOf("pending-before-restart"), a.repository.snapshotJournal().map { it.eventId })
            assertNull(a.repository.profileTransfer.appliedOperation(old))
        }
    }

    @Test(timeout = 60_000) fun `new deck after completed consent blocks automatic history until a fresh review`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable(); pair(a, b)
            val old = a.controller.state.value.profileConsent.operationId
            b.controller.setForeground(false)
            a.seed("__LATER_TARGET__", "__LATER_PACK__")
            a.append("later-deck-answer", "__LATER_TARGET__")
            b.controller.setForeground(true)
            eventually("Content edit must invalidate the previous completed consent") {
                !a.controller.state.value.profileConsent.complete && !b.controller.state.value.profileConsent.complete
            }
            delay(350)
            assertNull(b.db.chunkDao().pack("__LATER_PACK__"))
            assertTrue(b.repository.snapshotJournal().isEmpty())
            approveMerge(a, b)
            assertNotEquals(old, a.controller.state.value.profileConsent.operationId)
            assertNotNull(b.db.chunkDao().pack("__LATER_PACK__"))
            assertEquals(listOf("later-deck-answer"), b.repository.snapshotJournal().map { it.eventId })
            b.restart()
            eventually("Durable completion must reconnect after restart") {
                a.controller.state.value.profileConsent.complete && b.controller.state.value.profileConsent.complete
            }
            assertEquals(1, b.db.reviewDao().all().size)
        }
    }

    @Test(timeout = 60_000) fun `reset marker persists cutoff archives old history and new generation rejects old peers`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable()
            val oldInvitation = pair(a, b)
            a.append("archived-event")
            eventually("Initial history must reach the other device") { b.db.reviewDao().all().size == 1 }
            a.reset()
            assertTrue(a.controller.state.value.blocked)
            assertTrue(a.controller.state.value.boundaryReady)
            assertEquals(0, a.controller.state.value.peers)
            a.restart()
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
            a.controller.setEnabled(true)
            assertFalse(a.controller.state.value.enabled)
            a.controller.startNewHistory()
            a.controller.setEnabled(true)
            a.controller.pair(oldInvitation)
            assertEquals("history-mismatch", a.controller.state.value.error)
            assertTrue(a.repository.snapshotJournal((a.controller.historyBoundary() ?: 0L), a.controller.boundaryToken()).isEmpty())
            assertEquals(1, a.db.reviewDao().all().size)
            assertTrue(a.db.cardDao().all().isEmpty())
            b.reset(); b.controller.startNewHistory(); b.controller.setEnabled(true)
            val newInvitation = pair(a, b)
            assertNotEquals(PairedLanTransport.parseInvitation(oldInvitation).binding,
                PairedLanTransport.parseInvitation(newInvitation).binding)
            a.append("new-generation-event")
            eventually("Only post-reset history must converge") {
                b.repository.snapshotJournal((b.controller.historyBoundary() ?: 0L), b.controller.boundaryToken()).map { it.eventId } == listOf("new-generation-event")
            }
            assertEquals(2, b.db.reviewDao().all().size) // Archive is retained, never reimported.
            assertEquals(1, b.db.cardDao().card(TARGET, 0)!!.reps)
            assertEquals(a.repository.snapshotJournal((a.controller.historyBoundary() ?: 0L), a.controller.boundaryToken()),
                b.repository.snapshotJournal((b.controller.historyBoundary() ?: 0L), b.controller.boundaryToken()))
        }
    }

    @Test(timeout = 60_000) fun `manual restore revokes peers and blocks restart until an explicit progress reset`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable()
            val oldInvitation = pair(a, b)
            val record = answer("manual-restore-event")
            a.repository.restoreFromJsonl(ReviewRecord.json.encodeToString(ReviewRecord.serializer(), record))
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
            assertFalse(a.controller.state.value.boundaryReady)
            a.controller.ensureHistoryWritable() // Successful restore permits local learning, never old pairing.
            assertEquals(0, a.controller.state.value.peers)
            a.restart()
            a.controller.startNewHistory(); a.controller.setEnabled(true)
            a.controller.pair(oldInvitation)
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
            assertTrue(b.db.reviewDao().all().isEmpty())
            assertEquals(listOf("manual-restore-event"), a.repository.snapshotJournal().map { it.eventId })
        }
    }

    @Test(timeout = 60_000) fun `restart between persisted reset marker and reset completion stays fail closed`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable(); pair(a, b)
            a.append("pre-reset-event")
            eventually("Pre-reset history must converge") { b.db.reviewDao().all().size == 1 }
            a.controller.beforeHistoryChange()
            // Simulate process loss before the reset transaction and afterProgressReset acknowledgment.
            a.restart()
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.boundaryReady)
            a.controller.startNewHistory(); a.controller.setEnabled(true)
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
            assertEquals(listOf("pre-reset-event"), a.repository.snapshotJournal().map { it.eventId })
            a.reset()
            a.controller.startNewHistory(); a.controller.setEnabled(true)
            assertFalse(a.controller.state.value.blocked)
            assertTrue(a.controller.state.value.running)
            assertTrue(a.repository.snapshotJournal((a.controller.historyBoundary() ?: 0L), a.controller.boundaryToken()).isEmpty())
        }
    }

    @Test(timeout = 60_000) fun `concurrent enable cannot reopen a restore blocked installation`() = runBlocking {
        fixture { a, _ ->
            // Exercise first-load calls from separate dispatchers before explicit opt-in.
            coroutineScope {
                listOf(async(Dispatchers.Default) { (a.controller.historyBoundary() ?: 0L) },
                    async(Dispatchers.Default) { a.controller.undoBoundary() },
                    async(Dispatchers.Default) { a.controller.setForeground(true) }).awaitAll()
            }
            a.controller.setEnabled(true)
            coroutineScope {
                (0 until 12).map { index -> async(Dispatchers.Default) {
                    if (index % 2 == 0) a.controller.setEnabled(true)
                    else a.controller.beforeExternalRestore()
                } }.awaitAll()
            }
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.enabled)
            assertFalse(a.controller.state.value.running)
            a.restart()
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
        }
    }

    @Test(timeout = 60_000) fun `copied reset database without pairing configuration cannot expose archived history`() = runBlocking {
        fixture { a, b ->
            a.enable(); a.append("archived-on-source")
            a.reset(); a.controller.startNewHistory()
            a.close() // Checkpoint and close the actual SQLite store before copying it.
            val copiedHome = b.home.resolve("copied-installation").apply { mkdirs() }
            a.home.resolve("learner.db").copyTo(copiedHome.resolve("learner.db"))
            val copied = Device(copiedHome)
            try {
                copied.controller.setForeground(true)
                copied.controller.setEnabled(true)
                assertTrue(copied.controller.state.value.blocked)
                assertFalse(copied.controller.state.value.running)
                assertFalse(copied.controller.state.value.enabled)
                assertEquals(1, copied.db.reviewDao().all().size)
                try {
                    copied.repository.snapshotJournal()
                    fail("A missing boundary token must not export archived rows")
                } catch (_: IllegalArgumentException) { }
                copied.reset(); copied.controller.startNewHistory(); copied.controller.setEnabled(true)
                assertFalse(copied.controller.state.value.blocked)
                assertTrue(copied.repository.snapshotJournal(copied.controller.historyBoundary() ?: 0L,
                    copied.controller.boundaryToken()).isEmpty())
            } finally { copied.close() }
        }
    }

    @Test(timeout = 60_000) fun `stale restore completion cannot clear a newer pending reset`() = runBlocking {
        fixture { a, _ ->
            a.enable()
            val first = a.controller.beforeExternalRestore()
            a.controller.beforeHistoryChange()
            try {
                a.controller.afterExternalRestore(first)
                fail("Stale completion must be rejected")
            } catch (_: IllegalStateException) { }
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.boundaryReady)
            try {
                a.controller.ensureHistoryWritable()
                fail("Pending reset must still block local learning")
            } catch (_: IllegalStateException) { }
            a.restart()
            assertTrue(a.controller.state.value.blocked)
            assertFalse(a.controller.state.value.running)
        }
    }

    @Test(timeout = 60_000) fun `older fresh generation with answers cannot adopt a newer epoch after revoke`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable()
            a.reset(); b.reset()
            a.controller.startNewHistory(); b.controller.startNewHistory()
            a.controller.setEnabled(true); b.controller.setEnabled(true)
            pair(a, b)
            b.controller.setForeground(false)
            b.append("older-generation-answer")
            b.controller.revoke(); b.controller.setForeground(true); b.controller.setEnabled(true)
            a.reset(); a.controller.startNewHistory(); a.controller.setEnabled(true)
            a.controller.createInvitation("127.0.0.1")
            b.controller.pair(requireNotNull(a.controller.state.value.invitation))
            assertEquals("history-mismatch", b.controller.state.value.error)
            assertEquals(0, b.controller.state.value.peers)
            assertTrue(a.repository.snapshotJournal(a.controller.historyBoundary() ?: 0L,
                a.controller.boundaryToken()).isEmpty())
            b.reset(); b.controller.startNewHistory(); b.controller.setEnabled(true)
            pair(a, b)
            a.append("current-generation-answer")
            eventually("After explicit reset, only the current generation may converge") {
                b.repository.snapshotJournal(b.controller.historyBoundary() ?: 0L,
                    b.controller.boundaryToken()).map { it.eventId } == listOf("current-generation-answer")
            }
            assertEquals(2, b.db.reviewDao().all().size)
            assertEquals(1, b.db.cardDao().card(TARGET, 0)!!.reps)
        }
    }

    @Test(timeout = 60_000) fun `stop during incoming Room replay rolls back before returning`() = runBlocking {
        for (stopMode in listOf("background", "disable", "revoke")) {
            val enteredReplay = CompletableDeferred<Unit>()
            fixture(enteredReplay) { a, b ->
                a.enable(); b.enable(); pair(a, b)
                val callbacksBefore = a.merged.get()
                val signalBefore = a.sessionChanges.generation.value
                a.replayArmed = true
                b.append("inflight-event")
                withTimeout(15_000) { enteredReplay.await() }
                val stopped = async(Dispatchers.Default) {
                    when (stopMode) {
                        "background" -> a.controller.setForeground(false)
                        "disable" -> a.controller.setEnabled(false)
                        "revoke" -> a.controller.revoke()
                    }
                }
                delay(100)
                assertFalse("$stopMode must wait for the in-flight transaction barrier", stopped.isCompleted)
                a.replayRelease.complete(Unit)
                withTimeout(5_000) { stopped.await() }
                assertFalse("$stopMode must stop listening", a.controller.state.value.running)
                assertTrue("$stopMode must roll back the incoming journal", a.db.reviewDao().all().isEmpty())
                assertTrue(a.db.cardDao().all().isEmpty())
                assertEquals(callbacksBefore, a.merged.get())
                assertEquals(signalBefore, a.sessionChanges.generation.value)
                delay(350)
                assertTrue(a.db.reviewDao().all().isEmpty())
            }
        }
    }

    @Test fun `oversized single event stays visible and never advances peer history`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable(); pair(a, b)
            a.controller.setForeground(false); b.controller.setForeground(false)
            val event = answer("oversized-page-event").copy(timingDiscardReason = "x".repeat(300_000))
            a.repository.mergeJournal(listOf(event), a.controller.historyBoundary() ?: 0L, a.controller.boundaryToken())
            val peerSource = ReviewRecord.json.decodeFromString(
                dev.ikna.data.export.JournalStreamBinding.serializer(),
                requireNotNull(a.db.incrementalJournalDao().stream()).bindingJson).sourceDevice
            a.controller.setForeground(true); b.controller.setForeground(true)
            eventually("A must show the fixed actionable event-size error") {
                a.controller.state.value.error == "journal_item_too_large"
            }
            delay(750)
            assertEquals("journal_item_too_large", a.controller.state.value.error)
            assertFalse(a.controller.state.value.profileConsent.journalCaughtUp)
            assertEquals(1L, a.reviewCount())
            assertEquals(0L, b.reviewCount())
            assertEquals(0L, b.db.incrementalJournalDao().checkpoint(peerSource)?.sequence ?: 0L)
            assertEquals(event.eventId, a.repository.snapshotJournal().single().eventId)
            a.controller.setEnabled(false)
            assertNull(a.controller.state.value.error)
        }
    }

    @Test(timeout = 120_000) fun `approved history beyond bootstrap cap converges in bounded pages after restart`() = runBlocking {
        fixture { a, b ->
            a.enable(); b.enable(); pair(a, b)
            val operation = a.controller.state.value.profileConsent.operationId
            a.controller.setForeground(false); b.controller.setForeground(false)
            val padding = "__TECHNICAL_DIAGNOSTIC_PADDING__".padEnd(16_384, 'x')
            val left = (0 until 1100).map { answer("paged-left-$it").copy(timingDiscardReason = padding) }
            val right = (0 until 17).map { answer("paged-right-$it") }
            a.repository.mergeJournal(left, a.controller.historyBoundary() ?: 0L, a.controller.boundaryToken())
            b.repository.mergeJournal(right, b.controller.historyBoundary() ?: 0L, b.controller.boundaryToken())
            try {
                a.repository.profileTransfer.snapshot(a.controller.historyBoundary() ?: 0L, a.controller.boundaryToken())
                fail("The fixture must exceed the unchanged initial full-profile byte cap")
            } catch (_: dev.ikna.data.export.ProfileTransferLimitException) { }
            // Fresh controller/Room instances exercise durable consent and stream metadata.
            a.restart(); b.restart()
            // This finite multi-page test has a separate budget; existing small-case timeouts stay unchanged.
            try {
                withTimeout(60_000) {
                    while (a.reviewCount() != 1117L || b.reviewCount() != 1117L ||
                        !a.controller.state.value.profileConsent.journalCaughtUp ||
                        !b.controller.state.value.profileConsent.journalCaughtUp) delay(50)
                }
            } catch (timeout: TimeoutCancellationException) {
                fun status(d: Device): String {
                    val state = d.controller.state.value
                    val consent = state.profileConsent
                    return "running=${state.running}, error=${state.error}, complete=${consent.complete}, " +
                        "caughtUp=${consent.journalCaughtUp}, pending=${consent.journalPending}"
                }
                throw AssertionError("Paged convergence failed: A rows=${a.reviewCount()} ${status(a)}; " +
                    "B rows=${b.reviewCount()} ${status(b)}", timeout)
            }
            assertEquals(operation, a.controller.state.value.profileConsent.operationId)
            assertEquals(operation, b.controller.state.value.profileConsent.operationId)
            val expected = (left + right).map { it.eventId }.toSet()
            assertEquals(expected, a.repository.snapshotJournal().map { it.eventId }.toSet())
            assertEquals(a.repository.snapshotJournal(), b.repository.snapshotJournal())
            assertEquals(a.db.cardDao().all(), b.db.cardDao().all())
            assertEquals(1, a.db.cardDao().all().size)
            val cards = a.db.cardDao().all()
            val callbacks = a.merged.get() to b.merged.get()
            val previous = requireNotNull(a.controller.state.value.lastSuccessAtMillis)
            eventually("A caught-up no-op round must complete without replaying history") {
                (a.controller.state.value.lastSuccessAtMillis ?: 0L) > previous
            }
            assertEquals(callbacks, a.merged.get() to b.merged.get())
            assertEquals(cards, a.db.cardDao().all())
            assertEquals(1117, a.db.reviewDao().all().size)
            assertEquals(1117, b.db.reviewDao().all().size)
        }
    }

    companion object {
        private const val TARGET = "__SYNC_TECHNICAL_TARGET__"
        private const val PACK = "__SYNC_TECHNICAL_PACK__"
        private const val TS = 1577880000000L
        private fun answer(id: String, target: String = TARGET) = ReviewRecord(chunkId = target,
            ts = TS, rating = 3, eventId = id, durationMs = 100, prevIsNew = true)
    }
}
