package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.ReviewRecord
import dev.ikna.data.export.ProfileTransferCodec
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.*
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Real Room + controllers + pinned TLS, in one JVM. Does not claim physical-device/UI evidence. */
class DeviceProfileAdoptionIntegrationTest {
    private class Device(val home: File, val failBeforePointer: Boolean = false, val failAfterSelection: Boolean = false, val blockActivation: Boolean = false) {
        val original = home.resolve("learner.db")
        val slots = ProfileSlotStore(home, original, faultInjector = { phase ->
            if (failAfterSelection && phase == ProfileSlotStore.Phase.AFTER_SELECTION_COMMIT)
                error("__SYNTHETIC_AFTER_SELECTION_FAILURE__")
        })
        var db = openIknaDatabase(original)
        lateinit var repository: RestoreRepository
        lateinit var controller: DeviceSyncController
        lateinit var service: ProfileAdoptionService
        val sessionChanges = dev.ikna.domain.session.CommittedSessionChanges()
        var adoptionFinished = CompletableDeferred<Unit>()
        val activationEntered = CompletableDeferred<Unit>()
        val activationRelease = CompletableDeferred<Unit>()
        init { wire() }

        private fun wire() {
            repository = repo(db)
            controller = DeviceSyncController(home.resolve("sync"), repository, onMerged = { sessionChanges.notifyCommitted() },
                intervalMillis = 100, currentDatabaseSlot = slot(slots.selection().active),
                currentSlotOperationId = slots.selection().operationId,
                currentSlotOperationDigest = slots.selection().operationDigest)
            repository.historyBoundary = { controller.historyBoundary() }
            service = ProfileAdoptionService(slots, repository, ::openIknaDatabase, ::repo,
                // This fixture has no concurrent UI or background writers; production owns its write gate.
                withLiveWriteGuard = { it() }, quiesceAndCloseLive = { db.close() },
                prepareActivation = { candidate, operation, digest ->
                    controller.prepareProfileActivation(slot(candidate), operation, digest)
                    if (blockActivation) { activationEntered.complete(Unit); activationRelease.await() }
                    if (failBeforePointer) error("__SYNTHETIC_BEFORE_POINTER_FAILURE__")
                }, prepareRecovery = { operation ->
                    controller.prepareProfileRecovery(slot(requireNotNull(slots.selection().recovery)), operation)
                })
            controller.isRestartRequired = { service.state == ProfileAdoptionService.State.RESTART_REQUIRED }
            controller.adoptionHandler = { request ->
                try {
                    service.adopt(request.incoming, request.preview.localRevision, request.proposal.operationId,
                        ProfileConsentProtocol.tupleDigest(request.proposal), request.afterLocalId, request.boundaryToken, request.journalAuthorization)
                    adoptionFinished.complete(Unit)
                } catch (failure: Throwable) {
                    adoptionFinished.completeExceptionally(failure)
                    throw failure
                }
            }
        }
        private fun slot(file: File) = slots.slotId(file)
        suspend fun seed(id: String, translation: String = "__TEST__") {
            db.chunkDao().upsertPack(PackEntity(id, 1, "en", 1, TS, title = "__SYNTHETIC_DECK__"))
            db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(id, id, "en", "__FIXTURE__", "__FIXTURE__", translation, 0, 1, 1)))
            db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(id, id, 1)))
            repository.mergeJournal(listOf(ReviewRecord(chunkId = id, ts = TS, rating = 3,
                eventId = "event-$id", durationMs = 100, prevIsNew = true)))
        }
        suspend fun start() { controller.initializeProfileSlot(); controller.setForeground(true); controller.setEnabled(true) }
        suspend fun restart() {
            controller.close(); db.close()
            db = openIknaDatabase(slots.selection().active)
            adoptionFinished = CompletableDeferred()
            wire()
            controller.initializeProfileSlot()
            controller.setForeground(true)
        }
        suspend fun close() { controller.close(); db.close() }
    }
    private suspend fun fixture(failBeforePointer: Boolean = false, failAfterSelection: Boolean = false,
                                blockActivation: Boolean = false,
                                block: suspend (Device, Device) -> Unit) {
        val home = Files.createTempDirectory("ikna-controller-adoption-").toFile()
        val source = Device(home.resolve("source").apply { mkdirs() })
        val recipient = Device(home.resolve("recipient").apply { mkdirs() }, failBeforePointer, failAfterSelection, blockActivation)
        try { source.seed("__SOURCE__"); recipient.seed("__RECIPIENT__"); block(source, recipient) }
        catch (failure: AssertionError) {
            suspend fun diagnostics(device: Device): String {
                val state = device.controller.state.value
                val operation = state.profileConsent.operationId ?: device.slots.selection().operationId
                val receipt = operation?.let { runCatching { device.repository.profileTransfer.appliedOperation(it) }.getOrNull() }
                val profile = runCatching { ProfileTransferCodec.profileIdentity(device.repository.profileTransfer.snapshot()) }.getOrNull()
                return "$state ${state.profileConsent} receipt=${receipt?.resultingProfileIdentity} " +
                    "profile=$profile slot=${device.slots.slotId(device.slots.selection().active)}"
            }
            throw AssertionError("${failure.message}; source=${diagnostics(source)}; recipient=${diagnostics(recipient)}", failure)
        }
        finally { recipient.activationRelease.complete(Unit); try { source.close() } finally { recipient.close(); home.deleteRecursively() } }
    }
    private suspend fun eventually(message: String, condition: suspend () -> Boolean) {
        try { withTimeout(15_000) { while (!condition()) delay(25) } }
        catch (timeout: TimeoutCancellationException) { throw AssertionError(message, timeout) }
    }
    private suspend fun propose(source: Device, recipient: Device): String {
        source.start(); recipient.start()
        source.controller.createInvitation("127.0.0.1")
        recipient.controller.pair(requireNotNull(source.controller.state.value.invitation))
        eventually("Both peers must pair") {
            source.controller.state.value.peers == 1 && recipient.controller.state.value.peers == 1
        }
        eventually("Adoption proposal must bind both profile revisions") {
            if (source.controller.state.value.profileConsent.operationId == null)
                source.controller.proposeAdoption(source.controller.state.value.devices.single().id, useThisDevice = true)
            source.controller.state.value.profileConsent.operationId != null
        }
        val operation = requireNotNull(source.controller.state.value.profileConsent.operationId)
        eventually("Recipient must see exact adoption proposal") {
            recipient.controller.state.value.profileConsent.operationId == operation
        }
        return operation
    }

    @Test(timeout = 60_000) fun `bilateral adoption selects source journal retains content and recovery then reconnects`() = runBlocking {
        fixture { source, recipient ->
            val old = recipient.repository.profileTransfer.snapshot()
            val operation = propose(source, recipient)
            source.controller.approveProfile(operation)
            delay(300)
            assertEquals(old, recipient.repository.profileTransfer.snapshot())
            assertEquals(recipient.original, recipient.slots.selection().active)
            recipient.controller.approveProfile(operation)
            withTimeout(15_000) { recipient.adoptionFinished.await() }
            assertEquals(ProfileAdoptionService.State.RESTART_REQUIRED, recipient.service.state)
            assertEquals(recipient.original, recipient.slots.selection().recovery)
            recipient.restart()
            eventually("Recipient durable adoption receipt must converge with donor") {
                source.controller.state.value.profileConsent.complete && recipient.controller.state.value.profileConsent.complete
            }
            assertEquals(operation, recipient.controller.state.value.profileConsent.operationId)
            assertEquals(listOf("event-__SOURCE__"), recipient.repository.snapshotJournal().map { it.eventId })
            assertEquals(source.repository.snapshotJournal(), recipient.repository.snapshotJournal())
            assertEquals(setOf("__SOURCE__", "__RECIPIENT__"), recipient.db.chunkDao().packs().map { it.id }.toSet())
            assertEquals(setOf("__SOURCE__", "__RECIPIENT__"), source.db.chunkDao().packs().map { it.id }.toSet())
            assertEquals(1L, source.sessionChanges.generation.value) // Content-only donor commit notified once.
            val recovery = openIknaDatabase(recipient.original)
            try { assertEquals(old, repo(recovery).profileTransfer.snapshot()) } finally { recovery.close() }
            // Explicit recovery switches back only after durably revoking paired credentials.
            recipient.controller.setForeground(false)
            recipient.service.recover("explicit-recovery")
            recipient.restart()
            assertEquals(recipient.original, recipient.slots.selection().active)
            assertEquals(old, recipient.repository.profileTransfer.snapshot())
            assertFalse(recipient.controller.state.value.enabled)
            assertEquals(0, recipient.controller.state.value.peers)
            assertFalse(recipient.controller.state.value.profileConsent.complete)
            recipient.controller.ensureHistoryWritable()
            recipient.repository.mergeJournal(listOf(ReviewRecord(chunkId = "__RECIPIENT__", ts = TS + 1,
                rating = 3, eventId = "after-recovery", durationMs = 100)),
                recipient.controller.historyBoundary() ?: 0L, recipient.controller.boundaryToken())
            assertEquals(setOf("event-__RECIPIENT__", "after-recovery"),
                recipient.repository.snapshotJournal().map { it.eventId }.toSet())
        }
    }

    @Test(timeout = 60_000) fun `failure after activation preparation before pointer keeps original on startup`() = runBlocking {
        fixture(failBeforePointer = true) { source, recipient ->
            val old = recipient.repository.profileTransfer.snapshot()
            val operation = propose(source, recipient)
            source.controller.approveProfile(operation); recipient.controller.approveProfile(operation)
            try {
                withTimeout(15_000) { recipient.adoptionFinished.await() }
                fail("Expected injected activation interruption")
            } catch (expected: IllegalStateException) {
                assertEquals("__SYNTHETIC_BEFORE_POINTER_FAILURE__", expected.message)
            }
            assertEquals(recipient.original, recipient.slots.selection().active)
            recipient.restart()
            assertEquals(old, recipient.repository.profileTransfer.snapshot())
            assertFalse(recipient.controller.state.value.enabled)
            assertFalse(recipient.controller.state.value.running)
            assertFalse(recipient.controller.state.value.profileConsent.complete)
            assertNull(recipient.repository.profileTransfer.appliedOperation(operation))
        }
    }
    @Test(timeout = 60_000) fun `ambiguous failure after selector commit reconciles exact adoption on restart`() = runBlocking {
        fixture(failAfterSelection = true) { source, recipient ->
            val operation = propose(source, recipient)
            source.controller.approveProfile(operation); recipient.controller.approveProfile(operation)
            try {
                withTimeout(15_000) { recipient.adoptionFinished.await() }
                fail("Expected synthetic post-commit interruption")
            } catch (expected: IllegalStateException) {
                assertEquals("__SYNTHETIC_AFTER_SELECTION_FAILURE__", expected.message)
            }
            assertNotEquals(recipient.original, recipient.slots.selection().active)
            recipient.restart()
            eventually("Committed selector and pending tuple must recover the exact bilateral receipt") {
                source.controller.state.value.profileConsent.complete && recipient.controller.state.value.profileConsent.complete
            }
            assertEquals(operation, recipient.controller.state.value.profileConsent.operationId)
            assertEquals(listOf("event-__SOURCE__"), recipient.repository.snapshotJournal().map { it.eventId })
            assertEquals(recipient.original, recipient.slots.selection().recovery)
        }
    }

    @Test(timeout = 60_000) fun `history reset waiting behind adoption cannot invalidate the selected profile`() = runBlocking {
        fixture(blockActivation = true) { source, recipient ->
            val operation = propose(source, recipient)
            source.controller.approveProfile(operation); recipient.controller.approveProfile(operation)
            withTimeout(15_000) { recipient.activationEntered.await() }
            val reset = async(Dispatchers.Default) { runCatching { recipient.controller.beforeHistoryChange() } }
            delay(100)
            assertFalse("Reset must serialize behind adoption", reset.isCompleted)
            recipient.activationRelease.complete(Unit)
            withTimeout(15_000) { recipient.adoptionFinished.await() }
            val failure = withTimeout(5_000) { reset.await() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals("profile-restart-required", failure!!.message)
            try {
                recipient.controller.beforeExternalRestore()
                fail("Restore must not start after an adoption publishes a different database")
            } catch (expected: IllegalStateException) {
                assertEquals("profile-restart-required", expected.message)
            }
            recipient.restart()
            eventually("Rejected reset must leave the approved adoption reconnectable") {
                source.controller.state.value.profileConsent.complete && recipient.controller.state.value.profileConsent.complete
            }
            assertEquals(listOf("event-__SOURCE__"), recipient.repository.snapshotJournal().map { it.eventId })
        }
    }

    @Test(timeout = 60_000) fun `authoritative adoption resolves conflicting shared deck while keeping unique decks`() = runBlocking {
        fixture { source, recipient ->
            source.seed("__CONFLICT__", "__SOURCE_TEXT__")
            recipient.seed("__CONFLICT__", "__RECIPIENT_TEXT__")
            val operation = propose(source, recipient)
            assertFalse(source.controller.state.value.profileConsent.preview!!.canMerge)
            assertTrue(source.controller.state.value.profileConsent.preview!!.canAdopt)
            source.controller.approveProfile(operation)
            recipient.controller.approveProfile(operation)
            withTimeout(15_000) { recipient.adoptionFinished.await() }
            recipient.restart()
            eventually("Authoritative adoption with conflicting deck must converge") {
                source.controller.state.value.profileConsent.complete && recipient.controller.state.value.profileConsent.complete
            }
            assertEquals("__SOURCE_TEXT__", recipient.db.chunkDao().chunk("__CONFLICT__")!!.translation)
            assertEquals("__SOURCE_TEXT__", source.db.chunkDao().chunk("__CONFLICT__")!!.translation)
            assertEquals(setOf("__SOURCE__", "__RECIPIENT__", "__CONFLICT__"), recipient.db.chunkDao().packs().map { it.id }.toSet())
            assertEquals(setOf("__SOURCE__", "__RECIPIENT__", "__CONFLICT__"), source.db.chunkDao().packs().map { it.id }.toSet())
            assertEquals(source.repository.snapshotJournal(), recipient.repository.snapshotJournal())
        }
    }

    companion object {
        private const val TS = 1_700_000_000_000L
        private fun repo(db: IknaDatabase) = RestoreRepository(db, db.cardDao(), db.reviewDao(),
            db.statsDao(), db.planDao(), ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
            Scheduler(dayStartHour = 4), GovernorConfig())
    }
}
