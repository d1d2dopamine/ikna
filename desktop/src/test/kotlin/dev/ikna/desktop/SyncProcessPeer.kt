package dev.ikna.desktop

import androidx.room.useReaderConnection
import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.*
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.sync.DeviceSyncController
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Test-only child JVM. stdout is bounded machine IPC, never a retained credential log. */
object SyncProcessPeer {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size == 1)
        val peer = Peer(File(args.single()))
        emit(buildJsonObject { put("kind", "ready"); put("pid", ProcessHandle.current().pid()) })
        try {
            while (true) {
                val line = readBoundedLine() ?: break
                val request = Json.parseToJsonElement(line).jsonObject
                val id = request.getValue("id").jsonPrimitive.int
                try {
                    val value = peer.command(request)
                    emit(buildJsonObject { put("kind", "response"); put("id", id); put("value", value) })
                } catch (failure: Exception) {
                    // No exception text: pairing input and file paths must not leak through failures.
                    emit(buildJsonObject { put("kind", "response"); put("id", id); put("failure", failure.javaClass.simpleName) })
                }
            }
        } finally { peer.close() }
    }
    private fun readBoundedLine(): String? {
        val text = StringBuilder()
        while (true) {
            val value = System.`in`.read()
            if (value < 0) return if (text.isEmpty()) null else text.toString()
            if (value == 10) return text.toString()
            check(text.length < 65_536) { "Oversized test command" }
            text.append(value.toChar())
        }
    }
    @Synchronized private fun emit(value: JsonObject) { println(value.toString()); System.out.flush() }

    private class Peer(home: File) {
        private val db = openIknaDatabase(home.resolve("learner.db"))
        private val merged = AtomicInteger()
        private val cardWrites = AtomicInteger()
        @Volatile private var fault: String? = null
        private lateinit var controller: DeviceSyncController
        private val cards: CardDao = object : CardDao by db.cardDao() {
            override suspend fun upsertAll(cards: List<CardEntity>) {
                db.cardDao().upsertAll(cards)
                cardWrites.incrementAndGet()
                if (fault == "precommit") park("precommit", SyncProcessFixture.manifest(db, repository))
            }
        }
        private val repository: RestoreRepository = SyncProcessFixture.repository(db, cards,
            beforeRestore = { controller.beforeExternalRestore() }, afterRestore = { controller.afterExternalRestore(it) })
        init {
            controller = DeviceSyncController(home.resolve("sync"), repository, onMerged = {
                merged.incrementAndGet()
                if (fault == "postcommit") park("postcommit", SyncProcessFixture.manifest(db, repository))
            }, intervalMillis = 100)
            repository.historyBoundary = { controller.historyBoundary() }
            // Deliberately do not foreground/start transport before restart inspection.
        }
        private fun park(point: String, manifest: JsonObject): Nothing {
            fault = null
            emit(buildJsonObject { put("kind", "fault"); put("point", point);
                put("committed", point == "postcommit"); put("manifest", manifest) })
            CountDownLatch(1).await() // Only the orchestrator's force-kill may leave this barrier.
            error("Unreachable test barrier")
        }
        suspend fun command(request: JsonObject): JsonElement = when (request.getValue("command").jsonPrimitive.content) {
            "seed" -> {
                SyncProcessFixture.seed(db)
                if (request["legacy"]?.jsonPrimitive?.boolean == true) {
                    val raw = SyncProcessFixture.legacyRaw()
                    val original = db.reviewDao().insert(raw[0].toEntity())
                    db.reviewDao().insert(raw[1].copy(undoOf = original).toEntity())
                }
                JsonNull
            }
            "enable" -> { controller.setForeground(true); controller.setEnabled(true); JsonNull }
            "resume" -> { controller.setForeground(true); JsonNull }
            "pause" -> { controller.setForeground(false); JsonNull }
            "invite" -> { controller.createInvitation("127.0.0.1"); JsonPrimitive(requireNotNull(controller.state.value.invitation)) }
            "pair" -> { controller.pair(request.getValue("invitation").jsonPrimitive.content); JsonNull }
            "propose" -> { controller.proposeMerge(controller.state.value.devices.single().id); JsonNull }
            "approve" -> { controller.approveProfile(request.getValue("operation").jsonPrimitive.content); JsonNull }
            "append" -> {
                val rows = if (request.getValue("side").jsonPrimitive.content == "a") SyncProcessFixture.aRows() else SyncProcessFixture.bRows()
                repository.mergeJournal(rows, controller.historyBoundary() ?: 0, controller.boundaryToken())
                JsonNull
            }
            "arm" -> {
                val mode = request.getValue("point").jsonPrimitive.content
                require(mode == "precommit" || mode == "postcommit")
                fault = mode
                JsonNull
            }
            "manifest" -> SyncProcessFixture.manifest(db, repository)
            "status" -> {
                val state = controller.state.value
                buildJsonObject {
                    put("running", state.running); put("peers", state.peers)
                    put("preview", state.profileConsent.preview != null)
                    put("complete", state.profileConsent.complete); put("caughtUp", state.profileConsent.journalCaughtUp)
                    put("operation", state.profileConsent.operationId?.let(::JsonPrimitive) ?: JsonNull)
                    put("localApproved", state.profileConsent.localApproved)
                    put("remoteApproved", state.profileConsent.remoteApproved)
                    put("error", state.error?.let(::JsonPrimitive) ?: JsonNull)
                    put("rows", SyncProcessFixture.count(db, "reviews"))
                    put("merged", merged.get()); put("cardWrites", cardWrites.get())
                    put("lastSuccess", state.lastSuccessAtMillis ?: 0)
                }
            }
            else -> error("Unknown test command")
        }
        suspend fun close() { controller.close(); db.close() }
    }
}

/** Independent fixed input used by child writers and parent full-union replay oracle. */
internal object SyncProcessFixture {
    const val EXPECTED_COUNT = 277L
    private const val TARGET = "__PROCESS_SYNC_TECHNICAL_TARGET__"
    private const val PACK = "__PROCESS_SYNC_TECHNICAL_PACK__"
    private const val TS = 1577880000000L
    private fun answer(id: String?, offset: Long) = ReviewRecord(chunkId = TARGET, ts = TS + offset,
        rating = 3, eventId = id, durationMs = 100, prevIsNew = true)
    fun legacyRaw() = listOf(answer(null, 0).copy(id = 1),
        answer(null, 1).copy(id = 2, rating = 0, durationMs = 0, undoOf = 1))
    fun aRows(): List<ReviewRecord> = (0 until 257).map {
        answer("process-a-$it", 100L + it).copy(timingDiscardReason = "__TECHNICAL_PADDING__".padEnd(4096, 'x'))
    } + answer("process-a-undo", 400).copy(rating = 0, durationMs = 0, undoEventId = "process-a-0")
    fun bRows() = (0 until 17).map { answer("process-b-$it", 500L + it) }
    fun expected() = (JournalIdentity.normalize(legacyRaw()) + aRows() + bRows()).sortedBy { it.eventId }
    suspend fun seed(db: IknaDatabase) {
        db.chunkDao().upsertPack(PackEntity(PACK, 1, "en", 1, TS, title = "__TECHNICAL_FIXTURE__"))
        db.chunkDao().insertChunksIgnore(listOf(ChunkEntity(TARGET, PACK, "en", "__TECHNICAL_SENTINEL__",
            "__TECHNICAL_SENTINEL__", "__TECHNICAL_FIXTURE__", 0, 1, 1)))
        db.chunkDao().upsertPackChunks(listOf(PackChunkEntity(PACK, TARGET, 1)))
    }
    fun repository(db: IknaDatabase, cards: CardDao = db.cardDao(),
                   beforeRestore: suspend () -> Long = { 0L }, afterRestore: suspend (Long) -> Unit = {}) = RestoreRepository(
        db, cards, db.reviewDao(), db.statsDao(), db.planDao(),
        ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao(), db), Scheduler(dayStartHour = 4), GovernorConfig(),
        beforeExternalRestore = beforeRestore, afterExternalRestore = afterRestore)
    suspend fun count(db: IknaDatabase, table: String): Long {
        require(table in setOf("reviews", "journal_event_index"))
        return db.useReaderConnection { connection -> connection.usePrepared("SELECT COUNT(*) FROM $table") {
            check(it.step()); it.getLong(0)
        } }
    }
    suspend fun manifest(db: IknaDatabase, repository: RestoreRepository): JsonObject {
        val raw = db.reviewDao().all()
        val records = repository.snapshotJournal().sortedBy { it.eventId }
        val byRaw = raw.associateBy { it.id }
        val canonicalByRaw = raw.zip(JournalIdentity.normalize(raw.map { ReviewRecord.of(it) })).associate { it.first.id to it.second.eventId }
        val undoValid = raw.filter { it.undoOf != null }.all {
            byRaw[it.undoOf] != null && (it.undoEventId == null || it.undoEventId == canonicalByRaw[it.undoOf])
        }
        val stream = db.incrementalJournalDao().stream()
        val binding = stream?.let { ReviewRecord.json.decodeFromString<JournalStreamBinding>(it.bindingJson) }
        val checkpoint = binding?.let { db.incrementalJournalDao().checkpoint(it.targetDevice) }
        val remote = checkpoint?.let { ReviewRecord.json.decodeFromString<JournalStreamBinding>(it.bindingJson) }
        val remoteIndex = if (remote == null) emptyList() else db.incrementalJournalDao().page(remote.streamId, 0, Long.MAX_VALUE, 1000)
        val localIndex = if (binding == null) emptyList() else db.incrementalJournalDao().page(binding.streamId, 0, Long.MAX_VALUE, 1000)
        val localIndexValid = localIndex.withIndex().all { (position, entry) ->
            entry.sequence == position + 1L && canonicalByRaw[entry.reviewId] == entry.eventId
        } && (stream == null || (localIndex.size.toLong() == stream.sequence &&
            (localIndex.isEmpty() || localIndex.last().prefix == stream.prefix)))
        return buildJsonObject {
            put("rows", raw.size); put("undoValid", undoValid)
            put("journalDigest", ProfileTransferCodec.digest(ReviewRecord.json.encodeToString(records).toByteArray()))
            put("ids", JsonArray(records.map { JsonPrimitive(it.eventId!!) }))
            put("undos", JsonArray(records.filter { it.undoEventId != null }.map { JsonPrimitive("${it.eventId}->${it.undoEventId}") }))
            put("cards", JsonArray(db.cardDao().all().map { JsonPrimitive(it.toString()) }))
            put("components", JsonArray(db.componentDao().all().map { it.toString() }.sorted().map(::JsonPrimitive)))
            put("stats", JsonArray(db.statsDao().lastDays(1000).map { JsonPrimitive(it.toString()) }))
            put("streamId", binding?.streamId ?: "")
            put("operation", binding?.consentOperationId ?: "")
            put("sourceSequence", stream?.sequence ?: 0); put("sourcePrefix", stream?.prefix ?: "")
            put("remoteSequence", checkpoint?.sequence ?: 0); put("remotePrefix", checkpoint?.prefix ?: "")
            put("remoteIndexDigest", ProfileTransferCodec.digest(remoteIndex.joinToString("\n") { "${it.sequence}:${it.eventId}:${it.payloadDigest}:${it.undoEventId}:${it.prefix}" }.toByteArray()))
            put("remoteIndexCount", remoteIndex.size)
            put("localIndexValid", localIndexValid); put("localIndexCount", localIndex.size)
            put("localIndexDigest", ProfileTransferCodec.digest(localIndex.joinToString("\n") { "${it.sequence}:${it.eventId}:${it.payloadDigest}:${it.undoEventId}:${it.prefix}" }.toByteArray()))
        }
    }
}
