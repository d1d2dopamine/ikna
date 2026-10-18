package dev.ikna.sync

import dev.ikna.data.export.*
import dev.ikna.data.db.PackEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlin.test.*

class ProfileConsentProtocolTest {
    private val binding = HistoryBinding("profile", "epoch")
    private class FakeStore : ProfileConsentProtocol.Store {
        var generation = 0
        var validationFailure = false
        var supportsAdoption = false
        var adoptionProposal: ProfileConsentProtocol.Proposal? = null
        var commits = 0
        var journalStarts = 0
        var journalStore: FakeJournalStore? = null
        var saved: ProfileConsentProtocol.Completion? = null
        var invalidated: String? = null
        val operations = mutableMapOf<String, String>()
        var empty = ProfileTransferSnapshot(packs = emptyList(), chunks = emptyList(), tokens = emptyList(),
            memberships = emptyList(), contexts = emptyList(), reviews = emptyList())
        override suspend fun snapshot() = empty
        fun revision() = ProfileTransferCodec.digest("revision-$generation".toByteArray())
        override suspend fun snapshotWithRevision() = empty to revision()
        override suspend fun preview(incoming: ProfileTransferSnapshot) = ProfileMergePreview(
            revision(), ProfileTransferCodec.revision(incoming), 0, 0, 0, emptyList())
        override suspend fun commit(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                    operationId: String, tupleDigest: String): String {
            operations[operationId]?.let { require(it == tupleDigest); return ProfileTransferCodec.profileIdentity(empty) }
            require(preview.localRevision == revision()) { "stale" }
            operations[operationId] = tupleDigest; commits++
            empty = empty.copy(packs = (empty.packs + incoming.packs).distinctBy { it.id })
            return ProfileTransferCodec.profileIdentity(empty)
        }
        override suspend fun adopt(incoming: ProfileTransferSnapshot, preview: ProfileMergePreview,
                                   proposal: ProfileConsentProtocol.Proposal, localIsSource: Boolean): String? {
            check(supportsAdoption) { "adoption-not-supported" }
            adoptionProposal = proposal
            return if (localIsSource) commit(incoming, preview, proposal.operationId,
                ProfileConsentProtocol.tupleDigest(proposal)) else null
        }
        override suspend fun committedContentIdentity(operationId: String) = ProfileTransferCodec.contentIdentity(empty)
        override fun incremental(completion: ProfileConsentProtocol.Completion, peerId: String): IncrementalJournalProtocol {
            journalStarts++
            val local = if (completion.proposal.firstDevice == peerId) completion.proposal.secondDevice else completion.proposal.firstDevice
            return IncrementalJournalProtocol(FakeJournalStore(local, peerId, completion.proposal.operationId, completion.contentIdentity)
                .also { journalStore = it })
        }
        override suspend fun validCompletion(completion: ProfileConsentProtocol.Completion): Boolean {
            check(!validationFailure) { "synthetic-content-validation-failure" }
            return operations[completion.proposal.operationId] == ProfileConsentProtocol.tupleDigest(completion.proposal) &&
                completion.contentIdentity == ProfileTransferCodec.contentIdentity(empty)
        }
        override suspend fun saveCompletion(completion: ProfileConsentProtocol.Completion?) { saved = completion }
        override suspend fun saveInvalidatedOperationId(operationId: String?) { invalidated = operationId }
    }
    private class Pairing(val a: ProfileConsentProtocol, val b: ProfileConsentProtocol,
                          val left: FakeStore, val right: FakeStore) {
        suspend fun exchange() {
            b.receiveFrom("a", a.snapshotFor("b"))
            a.receiveFrom("b", b.snapshotFor("a"))
        }
        suspend fun propose(): String {
            exchange()
            val id = a.propose("b").operationId!!
            exchange()
            return id
        }
    }
    private fun pair(now: () -> Long = { 1000L }): Pairing {
        val a = FakeStore(); val b = FakeStore()
        return Pairing(ProfileConsentProtocol("a", binding, a, now), ProfileConsentProtocol("b", binding, b, now), a, b)
    }
    @Test fun `trust and proposal alone never mutate and both approvals are required`() = runBlocking {
        val p = pair(); val id = p.propose()
        assertEquals(0, p.left.commits); assertEquals(0, p.right.commits)
        p.a.approve(id); p.exchange()
        assertEquals(0, p.left.commits); assertEquals(0, p.right.commits)
        assertEquals(0, p.left.journalStarts)
        p.b.approve(id); repeat(3) { p.exchange() }
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
        assertTrue(p.a.state.complete); assertTrue(p.b.state.complete)
        assertTrue(p.left.journalStarts > 0)
    }
    @Test fun `stale local revision does not rebase approved proposal`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.left.generation++
        p.b.approve(id)
        assertFails { p.exchange() }
        assertEquals(0, p.left.commits)
        assertFalse(p.a.state.complete)
    }
    @Test fun `expiry clears consent and replay cannot apply`() = runBlocking {
        var time = 1000L
        val p = pair { time }; val id = p.propose()
        p.a.approve(id); p.exchange()
        val old = p.a.snapshotFor("b")
        time += ProfileConsentProtocol.TTL_MILLIS + 1
        assertFails { p.b.approve(id) }
        assertFails { p.b.receiveFrom("a", old) }
        assertEquals(0, p.right.commits)
        assertFalse(p.b.state.localApproved)
    }
    @Test fun `revocation clears consent and authenticated peer identity is checked`() = runBlocking {
        val p = pair(); val id = p.propose(); p.a.approve(id)
        assertFails { p.a.receiveFrom("third-device", p.b.snapshotFor("a")) }
        p.a.clear()
        assertFails { p.a.approve(id) }
        assertNull(p.left.saved)
        assertEquals(0, p.left.commits)
    }
    @Test fun `completed receipt restarts require matching peer before automatic journal`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(3) { p.exchange() }
        val a = ProfileConsentProtocol("a", binding, p.left, { 1000L }, p.left.saved)
        val b = ProfileConsentProtocol("b", binding, p.right, { 1000L }, p.right.saved)
        assertFalse(a.state.complete)
        b.receiveFrom("a", a.snapshotFor("b")); a.receiveFrom("b", b.snapshotFor("a"))
        assertTrue(a.state.complete); assertTrue(b.state.complete)
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
    }
    @Test fun `lost final receipt retry cannot duplicate application`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id)
        // Drop the first outgoing applied receipt, then deliver its retry twice.
        p.b.snapshotFor("a")
        val retry = p.b.snapshotFor("a")
        p.a.receiveFrom("b", retry)
        p.a.receiveFrom("b", retry)
        p.exchange()
        assertTrue(p.a.state.complete); assertTrue(p.b.state.complete)
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
    }
    @Test fun `foreground pause clears pending approval`() = runBlocking {
        val p = pair(); val id = p.propose(); p.a.approve(id)
        p.a.pause()
        assertFalse(p.a.state.localApproved)
        assertNull(p.a.state.operationId)
        assertFails { p.a.approve(id) }
        assertEquals(0, p.left.commits)
    }
    @Test fun `adoption proposal stays explicit and unsupported storage never falls back to merge`() = runBlocking {
        val p = pair(); p.exchange()
        val id = p.a.propose("b", ProfileConsentProtocol.Mode.ADOPT_OTHER).operationId!!
        p.exchange()
        assertEquals(ProfileConsentProtocol.Mode.ADOPT_OTHER, p.b.state.mode)
        p.a.approve(id); p.exchange()
        assertEquals(0, p.left.commits); assertEquals(0, p.right.commits)
        assertFails { p.b.approve(id) }
        assertEquals(0, p.left.commits); assertEquals(0, p.right.commits)
        assertFalse(p.b.state.complete)
    }
    @Test fun `new content invalidates completion and fresh bilateral preview can merge again`() = runBlocking {
        val p = pair(); val old = p.propose()
        p.a.approve(old); p.exchange(); p.b.approve(old); repeat(3) { p.exchange() }
        p.b.pause()
        p.left.empty = p.left.empty.copy(packs = listOf(PackEntity("new", 1, "en", 0, 1L)))
        p.left.generation++
        p.exchange()
        assertFalse(p.a.state.complete); assertFalse(p.b.state.complete)
        assertTrue(p.right.empty.packs.isEmpty())
        val next = p.propose()
        assertNotEquals(old, next)
        p.a.approve(next); p.b.approve(next); repeat(3) { p.exchange() }
        assertTrue(p.a.state.complete); assertTrue(p.b.state.complete)
        assertEquals(listOf("new"), p.right.empty.packs.map { it.id })
        assertEquals(2, p.left.commits); assertEquals(2, p.right.commits)
    }
    @Test fun `delayed same proposal without receipt cannot erase completed consent`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange()
        val beforePeerApproval = p.b.snapshotFor("a")
        val approvedWithoutReceipt = p.a.snapshotFor("b")
        p.b.approve(id)
        repeat(3) { p.exchange() }
        assertTrue(p.a.state.complete)
        p.a.receiveFrom("b", beforePeerApproval)
        p.b.receiveFrom("a", approvedWithoutReceipt)
        assertTrue(p.b.state.complete)
        assertTrue(p.a.state.complete)
        assertNotNull(p.left.saved)
        p.exchange()
        assertTrue(p.b.state.complete)
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
    }
    @Test fun `delayed discovery without proposal cannot erase completed consent`() = runBlocking {
        val p = pair(); p.exchange()
        val discovery = p.b.snapshotFor("a")
        val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(3) { p.exchange() }
        p.a.receiveFrom("b", discovery)
        assertTrue(p.a.state.complete)
        assertNotNull(p.left.saved)
        p.exchange()
        assertTrue(p.b.state.complete)
    }
    @Test fun `old invalidation cannot erase a newer completed operation`() = runBlocking {
        val p = pair(); val old = p.propose()
        p.a.approve(old); p.exchange(); p.b.approve(old); repeat(3) { p.exchange() }
        p.left.empty = p.left.empty.copy(packs = listOf(PackEntity("later", 1, "en", 0, 1L)))
        p.left.generation++
        val invalidation = p.a.snapshotFor("b")
        p.exchange()
        val next = p.propose()
        p.a.approve(next); p.b.approve(next); repeat(3) { p.exchange() }
        p.b.receiveFrom("a", invalidation)
        assertTrue(p.b.state.complete)
        assertEquals(next, p.b.state.operationId)
        assertNotNull(p.right.saved)
        Unit
    }
    @Test fun `content invalidation survives restart before peer receives it`() = runBlocking {
        val p = pair(); val old = p.propose()
        p.a.approve(old); p.exchange(); p.b.approve(old); repeat(3) { p.exchange() }
        p.left.empty = p.left.empty.copy(packs = listOf(PackEntity("restart-deck", 1, "en", 0, 1L)))
        p.left.generation++
        p.a.snapshotFor("b") // Persist invalidation, but lose the outgoing message.
        assertEquals(old, p.left.invalidated)
        assertNull(p.left.saved)
        val a = ProfileConsentProtocol("a", binding, p.left, { 1000L }, p.left.saved, p.left.invalidated)
        val recovered = Pairing(a, p.b, p.left, p.right)
        val next = recovered.propose()
        assertNotEquals(old, next)
        a.approve(next); p.b.approve(next); repeat(3) { recovered.exchange() }
        assertTrue(a.state.complete); assertTrue(p.b.state.complete)
        assertNull(p.left.invalidated); assertNull(p.right.invalidated)
    }
    @Test fun `durable cancellation wins crash before completion file deletion`() = runBlocking {
        val p = pair(); val old = p.propose()
        p.a.approve(old); p.exchange(); p.b.approve(old); repeat(3) { p.exchange() }
        // Model the safe write order: tombstone durable, old checkpoint still present.
        p.left.invalidated = old
        val a = ProfileConsentProtocol("a", binding, p.left, { 1000L }, p.left.saved, p.left.invalidated)
        p.b.receiveFrom("a", a.snapshotFor("b"))
        assertFalse(a.state.complete); assertFalse(p.b.state.complete)
        assertNull(p.left.saved)
        assertEquals(old, p.left.invalidated)
    }
    @Test fun `recipient restart completion supplies lost adoption approval but never local consent`() = runBlocking {
        val p = pair(); p.left.supportsAdoption = true; p.right.supportsAdoption = true
        p.exchange()
        val id = p.a.propose("b", ProfileConsentProtocol.Mode.ADOPT_THIS).operationId!!
        p.exchange(); p.a.approve(id); p.exchange()
        p.b.approve(id) // Recipient stages and stops before sending its approval frame.
        assertEquals(0, p.left.commits)
        val tuple = requireNotNull(p.right.adoptionProposal)
        p.right.operations[id] = ProfileConsentProtocol.tupleDigest(tuple)
        val completed = ProfileConsentProtocol.Completion(tuple, ProfileTransferCodec.profileIdentity(p.right.empty),
            ProfileTransferCodec.contentIdentity(p.right.empty))
        val restarted = ProfileConsentProtocol("b", binding, p.right, { 1000L }, completed)
        p.a.receiveFrom("b", restarted.snapshotFor("a"))
        assertEquals(1, p.left.commits)
        assertTrue(p.a.state.complete)
        restarted.receiveFrom("a", p.a.snapshotFor("b"))
        assertTrue(restarted.state.complete)
        assertEquals(id, p.a.state.operationId)
    }
    @Test fun `durable remote adoption completion cannot invent missing local approval`() = runBlocking {
        val p = pair(); p.left.supportsAdoption = true
        p.exchange()
        val id = p.a.propose("b", ProfileConsentProtocol.Mode.ADOPT_THIS).operationId!!
        val frame = Json.parseToJsonElement(p.a.snapshotFor("b").toString(Charsets.UTF_8)).jsonObject
        val tuple = Json.decodeFromString<ProfileConsentProtocol.Proposal>(frame.getValue("proposal").toString())
        p.right.operations[id] = ProfileConsentProtocol.tupleDigest(tuple)
        val completion = ProfileConsentProtocol.Completion(tuple, ProfileTransferCodec.profileIdentity(p.right.empty),
            ProfileTransferCodec.contentIdentity(p.right.empty))
        val restarted = ProfileConsentProtocol("b", binding, p.right, { 1000L }, completion)
        assertFails { p.a.receiveFrom("b", restarted.snapshotFor("a")) }
        assertEquals(0, p.left.commits)
        assertFalse(p.a.state.complete)
    }
    @Test fun `adoption completion with lost approval cannot rebase changed donor revision`() = runBlocking {
        val p = pair(); p.left.supportsAdoption = true; p.right.supportsAdoption = true
        p.exchange()
        val id = p.a.propose("b", ProfileConsentProtocol.Mode.ADOPT_THIS).operationId!!
        p.exchange(); p.a.approve(id); p.exchange(); p.b.approve(id)
        val tuple = requireNotNull(p.right.adoptionProposal)
        p.right.operations[id] = ProfileConsentProtocol.tupleDigest(tuple)
        val completion = ProfileConsentProtocol.Completion(tuple, ProfileTransferCodec.profileIdentity(p.right.empty),
            ProfileTransferCodec.contentIdentity(p.right.empty))
        val restarted = ProfileConsentProtocol("b", binding, p.right, { 1000L }, completion)
        p.left.generation++
        assertFails { p.a.receiveFrom("b", restarted.snapshotFor("a")) }
        assertEquals(0, p.left.commits)
        assertFalse(p.a.state.complete)
    }
    @Test fun `adoption receipt content mismatch never publishes completed checkpoint`() = runBlocking {
        val p = pair(); p.left.supportsAdoption = true; p.right.supportsAdoption = true
        p.exchange()
        val id = p.a.propose("b", ProfileConsentProtocol.Mode.ADOPT_THIS).operationId!!
        p.exchange(); p.a.approve(id); p.exchange(); p.b.approve(id)
        val tuple = requireNotNull(p.right.adoptionProposal)
        p.right.operations[id] = ProfileConsentProtocol.tupleDigest(tuple)
        val content = ProfileTransferCodec.contentIdentity(p.right.empty)
        val completion = ProfileConsentProtocol.Completion(tuple, ProfileTransferCodec.profileIdentity(p.right.empty), content)
        val restarted = ProfileConsentProtocol("b", binding, p.right, { 1000L }, completion)
        val mismatched = restarted.snapshotFor("a").toString(Charsets.UTF_8)
            .replace("\"contentIdentity\":\"$content\"", "\"contentIdentity\":\"${"0".repeat(64)}\"").toByteArray()
        assertFails { p.a.receiveFrom("b", mismatched) }
        assertEquals(1, p.left.commits) // The explicitly approved donor operation is still durable.
        assertFalse(p.a.state.complete)
        assertNull(p.left.saved)
    }
    @Test fun `legacy payload and over limit payload fail closed`() = runBlocking {
        val p = pair(); p.exchange()
        assertFails { p.a.receiveFrom("b", "{\"version\":1,\"records\":[]}".toByteArray()) }
        assertFails { p.a.receiveFrom("b", ByteArray(ProfileConsentProtocol.MAX_BYTES + 1)) }
        assertEquals(0, p.left.commits)
    }
    @Test fun `version two peer is explicit incompatibility and old completion needs fresh approval`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(3) { p.exchange() }
        val old = p.left.saved!!.copy(proposal = p.left.saved!!.proposal.copy(protocol = 2))
        val restarted = ProfileConsentProtocol("a", binding, p.left, { 1000L }, old)
        restarted.snapshotFor("b")
        assertFalse(restarted.state.complete)
        assertNull(p.left.saved)
        assertEquals(old.proposal.operationId, p.left.invalidated)
        assertFailsWith<UnsupportedSyncProtocolException> {
            restarted.receiveFrom("b", "{\"protocol\":2}".toByteArray())
        }
        assertEquals(1, p.left.commits)
    }

    @Test fun `old and future envelopes reject before nested records or durable validation`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        val saved = p.left.saved
        p.left.validationFailure = true // Must not be consulted for an incompatible envelope.
        for (version in listOf(2, 3, 5)) {
            assertFailsWith<UnsupportedSyncProtocolException> {
                p.a.receiveFrom("b", "{\"protocol\":$version,\"snapshot\":\"invalid-nested-records\"}".toByteArray())
            }
            assertEquals(saved, p.left.saved)
            assertNull(p.left.invalidated)
            assertEquals(1, p.left.commits)
            assertFalse(p.a.state.journalCaughtUp)
        }
    }

    @Test fun `v4 envelopes resume existing v3 consent and stream identities without bootstrap`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        val leftReceipt = p.left.saved!!; val rightReceipt = p.right.saved!!
        assertEquals(3, leftReceipt.proposal.protocol)
        val digest = ProfileConsentProtocol.tupleDigest(leftReceipt.proposal)
        val a = ProfileConsentProtocol("a", binding, p.left, { 1000L }, leftReceipt)
        val b = ProfileConsentProtocol("b", binding, p.right, { 1000L }, rightReceipt)
        repeat(8) {
            val wire = a.snapshotFor("b")
            val envelope = Json.parseToJsonElement(wire.toString(Charsets.UTF_8)).jsonObject
            assertEquals(kotlinx.serialization.json.JsonPrimitive(4), envelope["protocol"])
            assertNull(envelope["snapshot"])
            b.receiveFrom("a", wire); a.receiveFrom("b", b.snapshotFor("a"))
        }
        assertTrue(a.state.journalCaughtUp); assertTrue(b.state.journalCaughtUp)
        assertEquals(leftReceipt, p.left.saved); assertEquals(rightReceipt, p.right.saved)
        assertEquals(digest, ProfileConsentProtocol.tupleDigest(p.left.saved!!.proposal))
        assertNull(p.left.invalidated); assertNull(p.right.invalidated)
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
    }

    @Test fun `purpose aware incremental exchange preserves optional answer and undo`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        val source = p.left.journalStore!!
        source.add("optional-answer")
        source.add("optional-undo", "optional-answer")
        source.records.replaceAll { _, record -> record.copy(purpose = "optional_hard") }
        repeat(24) { p.exchange() }
        val received = p.right.journalStore!!.records
        assertEquals(source.records, received)
        assertEquals("optional_hard", received.getValue("optional-answer").purpose)
        assertEquals("optional_hard", received.getValue("optional-undo").purpose)
        assertEquals("optional-answer", received.getValue("optional-undo").undoEventId)
        assertTrue(p.a.state.journalCaughtUp); assertTrue(p.b.state.journalCaughtUp)
        assertEquals(1, p.left.commits); assertEquals(1, p.right.commits)
    }

    @Test fun `profile wire retains optional purpose and rejects unknown purpose before preview`() = runBlocking {
        val p = pair()
        p.left.empty = p.left.empty.copy(reviews = listOf(ReviewRecord(
            chunkId = "technical-fixture", ts = 1, rating = 3,
            eventId = "optional-answer", purpose = "optional_hard")))
        val wire = p.a.snapshotFor("b").toString(Charsets.UTF_8)
        assertTrue(wire.contains("\"purpose\":\"optional_hard\""))
        p.b.receiveFrom("a", wire.toByteArray())
        assertNotNull(p.b.state.preview)
        val clean = pair()
        assertFails {
            clean.b.receiveFrom("a", wire.replace("optional_hard", "unknown_purpose").toByteArray())
        }
        assertNull(clean.b.state.preview)
        assertEquals(0, clean.right.commits)
    }

    @Test fun `post consent journal proof failure removes caught up state without new mutation`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        assertTrue(p.a.state.journalCaughtUp)
        p.left.journalStore!!.failHead = true
        assertFailsWith<JournalReconciliationRequiredException> { p.a.snapshotFor("b") }
        assertTrue(p.a.state.complete)
        assertFalse(p.a.state.journalCaughtUp)
        assertTrue(p.a.state.journalPending)
        assertEquals(1, p.left.commits)
    }

    @Test fun `completion validation failure clears current catch up before incremental actor is entered`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        assertTrue(p.a.state.journalCaughtUp)
        p.left.validationFailure = true
        assertFails { p.a.snapshotFor("b") }
        assertFalse(p.a.state.journalCaughtUp)
        assertTrue(p.a.state.journalPending)
        p.left.validationFailure = false
        repeat(3) { p.exchange() }
        assertTrue(p.a.state.journalCaughtUp)
        p.left.validationFailure = true
        assertFails { p.a.receiveFrom("b", p.b.snapshotFor("a")) }
        assertFalse(p.a.state.journalCaughtUp)
        assertTrue(p.a.state.journalPending)
    }

    @Test fun `bare completion cannot restore cached caught up after a new local answer`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        assertTrue(p.a.state.journalCaughtUp)
        p.left.journalStore!!.add("new-local-answer")
        val restartedPeer = ProfileConsentProtocol("b", binding, p.right, { 1000L }, p.right.saved)
        p.a.receiveFrom("b", restartedPeer.snapshotFor("a")) // completion only, no journal head
        assertTrue(p.a.state.complete)
        assertFalse(p.a.state.journalCaughtUp)
        assertTrue(p.a.state.journalPending)
        p.a.snapshotFor("b")
        assertFalse(p.a.state.journalCaughtUp) // new outgoing row has not been acknowledged
    }

    @Test fun `enclosing consent wire preserves near limit incremental page encoding`() = runBlocking {
        val p = pair(); val id = p.propose()
        p.a.approve(id); p.exchange(); p.b.approve(id); repeat(8) { p.exchange() }
        val left = p.left.journalStore!!
        repeat(128) { index ->
            val event = "padded-$index"
            left.add(event)
            left.records[event] = left.records.getValue(event).copy(timingDiscardReason = "__TECHNICAL_PADDING__".padEnd(4096, 'x'))
        }
        val head = left.localHead()
        var selectedCount = 0
        for (count in 1..128) {
            left.pageSize = count
            val page = left.inventory(FakeJournalStore.seed(head.binding), head.highWater)!!
            val data = JournalData(page, left.records.values.take(count))
            if (ReviewRecord.json.encodeToString(data).toByteArray().size > IncrementalJournalLimits.MAX_DATA_BYTES) break
            selectedCount = count
        }
        left.pageSize = selectedCount
        var captured = false
        repeat(30) {
            if (!captured) {
                val wire = p.a.snapshotFor("b")
                val element = Json.parseToJsonElement(wire.toString(Charsets.UTF_8)).jsonObject["incremental"]
                val data = element?.takeUnless { it == JsonNull }?.jsonObject?.get("data")
                if (data != null && data != JsonNull) {
                    val rawFrame = element.toString()
                    val frame = Json.decodeFromString<IncrementalJournalProtocol.Frame>(rawFrame)
                    val canonicalFrame = ReviewRecord.json.encodeToString(frame)
                    assertEquals(canonicalFrame, rawFrame)
                    assertTrue(rawFrame.toByteArray().size <= IncrementalJournalLimits.MAX_FRAME_BYTES)
                    assertTrue(wire.size <= ProfileConsentProtocol.MAX_BYTES)
                    assertEquals(selectedCount, frame.data!!.records.size)
                    println("Synthetic actual outer wire sizing: outerBytes=${wire.size}, frameBytes=${rawFrame.toByteArray().size}, entries=$selectedCount")
                    captured = true
                }
                p.b.receiveFrom("a", wire)
                p.a.receiveFrom("b", p.b.snapshotFor("a"))
            }
        }
        assertTrue(captured, "Near-limit data must be emitted through actual consent encoding")
    }

}
