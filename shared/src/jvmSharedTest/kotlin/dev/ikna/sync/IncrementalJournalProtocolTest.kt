package dev.ikna.sync

import dev.ikna.data.export.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class IncrementalJournalProtocolTest {
    private suspend fun converge(a: IncrementalJournalProtocol, b: IncrementalJournalProtocol) {
        repeat(150) {
            b.receive(a.snapshot()); a.receive(b.snapshot())
            if (a.state.caughtUp && b.state.caughtUp) return
        }
        fail("Synthetic peers did not catch up: a=${a.state}, b=${b.state}")
    }
    @Test fun `bidirectional pages exchange only missing payload and terminate echoes`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        left.add("shared"); right.add("shared")
        repeat(9) { left.add("a-$it"); right.add("b-$it") }
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        converge(a, b)
        assertEquals(left.records, right.records)
        assertEquals(19, left.records.size)
        assertEquals(18, left.payloadRecords + right.payloadRecords)
        val sent = left.payloadRecords + right.payloadRecords
        repeat(5) { b.receive(a.snapshot()); a.receive(b.snapshot()) }
        assertEquals(sent, left.payloadRecords + right.payloadRecords)
        assertTrue(a.state.caughtUp); assertTrue(b.state.caughtUp)
    }
    @Test fun `receiver restart after request and lost acknowledgement resumes durably`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        repeat(5) { left.add("event-$it") }
        val a = IncrementalJournalProtocol(left)
        var b = IncrementalJournalProtocol(right)
        var data: IncrementalJournalProtocol.Frame? = null
        repeat(20) {
            if (data == null) {
                val outgoing = a.snapshot()
                if (outgoing.data != null) data = outgoing else { b.receive(outgoing); a.receive(b.snapshot()) }
            }
        }
        val first = assertNotNull(data)
        b = IncrementalJournalProtocol(right) // request is not durable and must not be required again
        b.receive(first)
        val committed = right.commits
        b.snapshot() // drop durable ACK
        b = IncrementalJournalProtocol(right)
        b.receive(first); b.receive(first)
        assertEquals(committed, right.commits)
        converge(a, b)
        assertEquals(left.records, right.records)
    }
    @Test fun `sender restart and reordered duplicate inventory preserve checkpoint`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        repeat(6) { left.add("event-$it") }
        var a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        val head = left.localHead()
        val first = left.inventory(FakeJournalStore.seed(head.binding), head.highWater)!!
        val second = left.inventory(first.to, head.highWater)!!
        assertFails { b.receive(IncrementalJournalProtocol.Frame(head, inventory = second)) }
        assertEquals(0L, right.checkpoints[head.binding]!!.sequence)
        converge(a, b)
        val committed = right.commits
        b.receive(IncrementalJournalProtocol.Frame(head, inventory = first))
        assertEquals(committed, right.commits)
        a = IncrementalJournalProtocol(left)
        converge(a, b)
        assertEquals(left.records, right.records)
    }
    @Test fun `failed apply never advances durable checkpoint or reports caught up`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        left.add("event")
        val head = left.localHead(); val page = left.inventory(FakeJournalStore.seed(head.binding), head.highWater)!!
        val b = IncrementalJournalProtocol(right)
        right.failApply = true
        val data = left.payload(JournalRequest(page, listOf("event")))
        assertFails { b.receive(IncrementalJournalProtocol.Frame(head, data = data)) }
        assertEquals(0L, right.checkpoints[head.binding]!!.sequence)
        assertTrue(right.records.isEmpty()); assertFalse(b.state.caughtUp)
        right.failApply = false
        b.receive(IncrementalJournalProtocol.Frame(head, data = data))
        assertEquals(1L, right.checkpoints[head.binding]!!.sequence)
    }
    @Test fun `same event ID with different payload fails without acknowledgement`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        left.add("event"); right.add("event")
        right.records["event"] = right.records.getValue("event").copy(rating = 1)
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        assertFails { converge(a, b) }
        assertTrue(right.checkpoints.values.all { it.sequence == 0L })
    }
    @Test fun `undo dependency can cross a page and remains explicit`() = runBlocking {
        val left = FakeJournalStore("a", "b", pageSize = 1); val right = FakeJournalStore("b", "a", pageSize = 1)
        left.add("answer"); left.add("undo", "answer")
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        converge(a, b)
        assertEquals("answer", right.records.getValue("undo").undoEventId)
        assertEquals(2, right.records.size)
    }
    @Test fun `unindexed committed tail prevents caught up`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        left.hasUnindexed = true
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        repeat(5) { b.receive(a.snapshot()); a.receive(b.snapshot()) }
        assertFalse(a.state.caughtUp); assertFalse(b.state.caughtUp)
        left.add("newly-indexed"); left.hasUnindexed = false
        converge(a, b)
    }
    @Test fun `wrong scope version oversized frame and forged resume cursor fail closed`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        val head = left.localHead()
        assertFailsWith<UnsupportedSyncProtocolException> {
            b.receive(IncrementalJournalProtocol.Frame(head.copy(binding = head.binding.copy(protocol = 2))))
        }
        assertFails { b.receive(IncrementalJournalProtocol.Frame(head.copy(binding = head.binding.copy(epoch = "other")))) }
        assertFailsWith<IncrementalJournalLimitException> { b.receive(IncrementalJournalProtocol.Frame(head.copy(binding = head.binding.copy(streamId = "x".repeat(300_000))))) }
        assertFails { a.receive(IncrementalJournalProtocol.Frame(right.localHead(), ack = JournalAck(head.binding,
            JournalCursor(0, FakeJournalStore.hash("forged"))))) }
        assertFalse(a.state.caughtUp); assertFalse(b.state.caughtUp)
    }
    @Test fun `lost durable stream proof clears prior caught up claim and never rebuilds`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        converge(a, b)
        assertTrue(a.state.caughtUp)
        left.failHead = true
        assertFailsWith<JournalReconciliationRequiredException> { a.snapshot() }
        assertFalse(a.state.caughtUp)
        assertTrue(a.state.pending)
        assertEquals(0, left.commits)
    }

    @Test fun `source restart may replace pending inventory with newer fixed high water`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        left.add("first")
        val b = IncrementalJournalProtocol(right)
        val oldHead = left.localHead()
        val oldPage = left.inventory(FakeJournalStore.seed(oldHead.binding), oldHead.highWater)!!
        b.receive(IncrementalJournalProtocol.Frame(oldHead, inventory = oldPage))
        left.add("second")
        val newerHead = left.localHead()
        val newerPage = left.inventory(FakeJournalStore.seed(newerHead.binding), newerHead.highWater)!!
        b.receive(IncrementalJournalProtocol.Frame(newerHead, inventory = newerPage))
        // An already sent old payload can still commit its exact contiguous prefix.
        b.receive(IncrementalJournalProtocol.Frame(oldHead,
            data = left.payload(JournalRequest(oldPage, listOf("first")))))
        converge(IncrementalJournalProtocol(left), b)
        assertEquals(left.records, right.records)
    }

    @Test fun `inbound commit cannot report caught up before new local echo is acknowledged`() = runBlocking {
        val left = FakeJournalStore("a", "b"); val right = FakeJournalStore("b", "a")
        val a = IncrementalJournalProtocol(left); val b = IncrementalJournalProtocol(right)
        converge(a, b)
        left.add("new-offline-event")
        val head = left.localHead()
        val page = left.inventory(FakeJournalStore.seed(head.binding), head.highWater)!!
        b.receive(IncrementalJournalProtocol.Frame(head,
            data = left.payload(JournalRequest(page, listOf("new-offline-event")))))
        assertFalse(b.state.outboundCaughtUp)
        assertFalse(b.state.caughtUp)
        assertTrue(b.state.pending)
        converge(a, b)
    }

    @Test fun `near limit repository page uses identical wire null encoding`() = runBlocking {
        val left = FakeJournalStore("a", "b", pageSize = 1)
        val right = FakeJournalStore("b", "a")
        repeat(128) {
            val id = "padded-$it"
            left.add(id)
            left.records[id] = left.records.getValue(id).copy(timingDiscardReason = "__TECHNICAL_PADDING__".padEnd(4096, 'x'))
        }
        val head = left.localHead()
        var selected: JournalData? = null
        for (count in 1..128) {
            left.pageSize = count
            val page = left.inventory(FakeJournalStore.seed(head.binding), head.highWater)!!
            val data = JournalData(page, left.records.values.take(count))
            if (ReviewRecord.json.encodeToString(data).toByteArray().size > IncrementalJournalLimits.MAX_DATA_BYTES) break
            selected = data
        }
        val data = assertNotNull(selected)
        left.pageSize = data.records.size
        val frame = IncrementalJournalProtocol.Frame(head, data = data)
        val repositoryBytes = ReviewRecord.json.encodeToString(data).toByteArray().size
        val oldWireBytes = Json { encodeDefaults = true }.encodeToString(frame).toByteArray().size
        val canonicalWireBytes = ReviewRecord.json.encodeToString(frame).toByteArray().size
        println("Synthetic page sizing: entries=${data.records.size}, repositoryBytes=$repositoryBytes, oldExplicitNullFrameBytes=$oldWireBytes, canonicalFrameBytes=$canonicalWireBytes")
        assertTrue(oldWireBytes > IncrementalJournalLimits.MAX_FRAME_BYTES)
        assertTrue(canonicalWireBytes <= IncrementalJournalLimits.MAX_FRAME_BYTES)
        val a = IncrementalJournalProtocol(left)
        val remoteHead = right.localHead()
        a.receive(IncrementalJournalProtocol.Frame(remoteHead,
            ack = JournalAck(head.binding, FakeJournalStore.seed(head.binding))))
        a.receive(IncrementalJournalProtocol.Frame(remoteHead,
            request = JournalRequest(data.inventory, data.records.map { it.eventId!! })))
        val emitted = listOf(a.snapshot(), a.snapshot()).first { it.data != null }
        assertEquals(data, emitted.data)
    }

}
