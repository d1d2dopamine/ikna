package dev.ikna.desktop

import dev.ikna.data.db.*
import dev.ikna.data.export.*
import dev.ikna.data.repo.ComponentRepository
import dev.ikna.data.repo.RestoreRepository
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.file.Files

/**
 * Opt-in, finite capacity evidence. This is deliberately not a JUnit test and has no
 * production caller. All content is technical identifiers, never study material.
 * Run each case in a fresh, externally time-limited JVM with -Xmx768m and one CPU.
 * Arguments: medium|near-cap|long-journal, revision label, metadata output path.
 */
object ProfileCapacityProbe {
    private const val TS = 1_760_000_000_000L
    private const val MAX_HEAP = 768L * 1024 * 1024
    private data class Case(val id: String, val targets: Int, val events: Int, val desiredBytes: Int? = null)
    private val cases = listOf(
        Case("medium", 512, 2_048),
        Case("near-cap", 2_048, 2_048, ProfileTransferCodec.MAX_BYTES - 65_536),
        Case("long-journal", 128, 4_096)
    )

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 3) { "Expected case, revision label and metadata output path" }
        val case = cases.single { it.id == args[0] }
        require(args[1].matches(Regex("[A-Za-z0-9._-]{1,64}")))
        require(Runtime.getRuntime().maxMemory() <= MAX_HEAP) { "Use -Xmx768m or less" }
        require(Runtime.getRuntime().availableProcessors() == 1) { "Use one CPU and -XX:ActiveProcessorCount=1" }
        val report = linkedMapOf<String, JsonElement>(
            "format" to JsonPrimitive(1), "case" to JsonPrimitive(case.id),
            "revision_label" to JsonPrimitive(args[1]), "status" to JsonPrimitive("running"),
            "targets" to JsonPrimitive(case.targets), "events" to JsonPrimitive(case.events),
            "packs" to JsonPrimitive(2), "memberships" to JsonPrimitive(case.targets * 2),
            "contexts" to JsonPrimitive(case.targets), "tokens" to JsonPrimitive(case.targets),
            "heap_max_bytes" to JsonPrimitive(Runtime.getRuntime().maxMemory()),
            "available_processors" to JsonPrimitive(Runtime.getRuntime().availableProcessors()),
            "java_version" to JsonPrimitive(System.getProperty("java.version")),
            "os_name" to JsonPrimitive(System.getProperty("os.name")),
            "os_arch" to JsonPrimitive(System.getProperty("os.arch")),
            "memory_note" to JsonPrimitive("Heap before/after values are instantaneous observations, not peaks or allocations. Measure process maximum RSS externally; it includes JVM/native overhead."),
            "timing_note" to JsonPrimitive("One observation per operation in a fresh JVM; fixture construction and half-journal seeding warm the code. Shared-host measurements, not device latency or a statistical benchmark.")
        )
        val output = File(args[2])
        output.parentFile?.mkdirs()
        fun save() { output.writeText(Json { prettyPrint = true }.encodeToString(JsonObject(report)) + "\n") }
        val timings = linkedMapOf<String, JsonElement>()
        suspend fun <T> phase(name: String, body: suspend () -> T): T {
            report["active_phase"] = JsonPrimitive(name)
            save()
            val runtime = Runtime.getRuntime()
            val heapBefore = runtime.totalMemory() - runtime.freeMemory()
            val gcBefore = ManagementFactory.getGarbageCollectorMXBeans().sumOf { maxOf(0L, it.collectionCount) }
            val start = System.nanoTime()
            try { return body() } finally {
                timings[name] = buildJsonObject {
                    put("elapsed_ns", System.nanoTime() - start)
                    put("heap_used_before_bytes", heapBefore)
                    put("heap_used_after_bytes", runtime.totalMemory() - runtime.freeMemory())
                    put("gc_collections", ManagementFactory.getGarbageCollectorMXBeans()
                        .sumOf { maxOf(0L, it.collectionCount) } - gcBefore)
                }
                report["operations"] = JsonObject(timings)
                save()
                println("PROFILE_CAPACITY ${case.id} $name complete")
            }
        }
        save()
        try {
            runBlocking {
                withTimeout(290_000L) {
                    val incoming = fixture(case)
                    val raw = ProfileTransferCodec.json.encodeToString(ProfileTransferCodec.canonical(incoming))
                        .toByteArray(Charsets.UTF_8)
                    report["canonical_input_sha256"] = JsonPrimitive(ProfileTransferCodec.digest(raw))
                    report["canonical_input_bytes"] = JsonPrimitive(raw.size)
                    report["byte_cap"] = JsonPrimitive(ProfileTransferCodec.MAX_BYTES)
                    report["row_cap_per_table"] = JsonPrimitive(ProfileTransferCodec.MAX_ROWS)
                    save()
                    val encoded = try { phase("encode") { ProfileTransferCodec.encode(incoming) } }
                    catch (failure: IllegalArgumentException) {
                        report["status"] = JsonPrimitive("rejected_at_encode")
                        report["rejection"] = JsonPrimitive(failure.message.orEmpty())
                        return@withTimeout
                    }
                    check(encoded.contentEquals(raw)) { "Canonical bytes changed" }
                    check(ProfileTransferCodec.decode(encoded) == ProfileTransferCodec.canonical(incoming))
                    check(incoming.chunks.size <= 5_000 && incoming.reviews.size <= 20_000)
                    val home = Files.createTempDirectory("ikna-profile-capacity-").toFile()
                    val db = openIknaDatabase(home.resolve("profile.db"))
                    try {
                        val repository = RestoreRepository(db, db.cardDao(), db.reviewDao(), db.statsDao(), db.planDao(),
                            ComponentRepository(db.componentDao(), db.chunkDao(), db.reviewDao()),
                            Scheduler(dayStartHour = 4), GovernorConfig())
                        phase("seed_existing_half_journal") {
                            db.inTransaction {
                                incoming.packs.forEach { db.chunkDao().upsertPack(it) }
                                db.chunkDao().insertChunksIgnore(incoming.chunks)
                                db.chunkDao().insertTokensIgnore(incoming.tokens)
                                db.chunkDao().upsertPackChunks(incoming.memberships)
                                db.chunkDao().upsertChunkContexts(incoming.contexts)
                            }
                            repository.mergeJournal(incoming.reviews.take(case.events / 2))
                        }
                        val beforePreview = repository.profileTransfer.snapshot()
                        val preview = phase("preview") { repository.profileTransfer.preview(incoming) }
                        check(preview.canMerge) { "Preview conflict: ${preview.conflicts}" }
                        check(preview.addedPacks == 0 && preview.addedTargets == 0 && preview.addedReviews == case.events / 2)
                        check(beforePreview == repository.profileTransfer.snapshot()) { "Preview mutated profile" }
                        val changed = phase("changed_merge") { repository.profileTransfer.commit(incoming, preview) }
                        check(changed.imported == case.events / 2 && changed.skipped == case.events / 2)
                        check(changed.replayed == case.events)
                        report["changed_merge_result"] = buildJsonObject {
                            put("imported", changed.imported); put("skipped", changed.skipped); put("replayed", changed.replayed)
                        }
                        val merged = repository.profileTransfer.snapshot()
                        check(ProfileTransferCodec.profileIdentity(merged) == ProfileTransferCodec.profileIdentity(incoming))
                        assertCardinalities(db, incoming, case)
                        val cardsBefore = db.cardDao().all().sortedBy { it.chunkId }
                        val statsBefore = db.statsDao().lastDays(Int.MAX_VALUE).sortedBy { it.day }
                        val duplicatePreview = phase("noop_preview") { repository.profileTransfer.preview(incoming) }
                        check(duplicatePreview.canMerge && duplicatePreview.addedReviews == 0)
                        val noOp = phase("noop_merge") { repository.profileTransfer.commit(incoming, duplicatePreview) }
                        check(noOp.imported == 0 && noOp.skipped == case.events && noOp.replayed == 0)
                        check(merged == repository.profileTransfer.snapshot()) { "Duplicate merge mutated snapshot" }
                        check(cardsBefore == db.cardDao().all().sortedBy { it.chunkId }) { "Duplicate merge mutated memories" }
                        check(statsBefore == db.statsDao().lastDays(Int.MAX_VALUE).sortedBy { it.day }) { "Duplicate merge mutated statistics" }
                        assertCardinalities(db, incoming, case)
                        report["noop_merge_result"] = buildJsonObject {
                            put("imported", noOp.imported); put("skipped", noOp.skipped); put("replayed", noOp.replayed)
                        }
                        // Same rows, one technical metadata field: exact cap+1, never a larger row sweep.
                        val first = incoming.packs.first()
                        val overCap = incoming.copy(packs = listOf(first.copy(title = first.title.orEmpty() +
                            "0".repeat(ProfileTransferCodec.MAX_BYTES + 1 - encoded.size))) + incoming.packs.drop(1))
                        val overBytes = ProfileTransferCodec.json.encodeToString(ProfileTransferCodec.canonical(overCap))
                            .toByteArray(Charsets.UTF_8).size
                        check(overBytes == ProfileTransferCodec.MAX_BYTES + 1)
                        val rejection = phase("same_rows_cap_plus_one_rejection") {
                            runCatching { ProfileTransferCodec.encode(overCap) }.exceptionOrNull()
                        }
                        check(rejection is IllegalArgumentException) { "Byte-cap overflow was accepted" }
                        report["cap_plus_one_rejected"] = JsonPrimitive(true)
                        report["exact_event_ids_preserved"] = JsonPrimitive(true)
                        report["one_memory_per_global_target"] = JsonPrimitive(true)
                        report["preview_read_only"] = JsonPrimitive(true)
                        report["noop_snapshot_cards_stats_unchanged"] = JsonPrimitive(true)
                        report["result_profile_identity"] = JsonPrimitive(ProfileTransferCodec.profileIdentity(merged))
                        report["status"] = JsonPrimitive("passed")
                    } finally { db.close(); check(home.deleteRecursively()) { "Disposable fixture cleanup failed" } }
                }
            }
        } catch (failure: Throwable) {
            report["status"] = JsonPrimitive("failed")
            report["failure_type"] = JsonPrimitive(failure.javaClass.name)
            report["failure_message"] = JsonPrimitive(failure.message.orEmpty())
            throw failure
        } finally {
            report.remove("active_phase")
            save()
        }
    }

    private suspend fun assertCardinalities(db: IknaDatabase, incoming: ProfileTransferSnapshot, case: Case) {
        val dao = db.chunkDao()
        check(dao.packs().size == 2)
        check(dao.allChunks().size == case.targets)
        check(dao.allTokens().size == case.targets)
        check(dao.allMemberships().size == case.targets * 2)
        check(dao.allContexts().size == case.targets)
        val reviews = db.reviewDao().all()
        check(reviews.size == case.events)
        check(reviews.map { it.eventId }.toSet() == incoming.reviews.map { it.eventId }.toSet())
        check(reviews.map { it.eventId }.distinct().size == case.events)
        val cards = db.cardDao().all()
        check(cards.size == case.targets && cards.all { it.level == 0 })
        check(cards.map { it.chunkId }.toSet() == incoming.chunks.map { it.id }.toSet())
    }

    private fun fixture(case: Case): ProfileTransferSnapshot {
        fun target(index: Int) = "t" + index.toString().padStart(6, '0')
        val packs = listOf("p000000", "p000001").map {
            PackEntity(it, 1, "zxx", case.targets, TS, title = "00000000")
        }
        val chunks = (0 until case.targets).map { index ->
            val id = target(index)
            ChunkEntity(id, packs.first().id, "zxx", id, "[$id]", "x" + id.drop(1), 1, 8, index + 1)
        }
        val tokens = chunks.map { ChunkTokenEntity(it.id, 0, it.id, it.id, "X", true, true, 1.0) }
        val memberships = packs.flatMap { pack -> chunks.map {
            PackChunkEntity(pack.id, it.id, it.freqRank,
                if (pack == packs.first()) "c" + it.id.drop(1) else null,
                if (pack == packs.first()) "m" + it.id.drop(1) else null)
        } }
        val contexts = chunks.map { ChunkContextEntity(packs.first().id, it.id, "c" + it.id.drop(1),
            "m" + it.id.drop(1), "technical-fixture", it.contextSentence, it.translation,
            it.targetStart, it.targetEnd, it.freqRank, tokensJson = "[]") }
        val reviews = (0 until case.events).map { index -> ReviewRecord(chunkId = target(index % case.targets),
            ts = TS + index * 1_000L, rating = 3, durationMs = 100, prevIsNew = true,
            eventId = "e" + index.toString().padStart(8, '0')) }
        val base = ProfileTransferSnapshot(packs = packs, chunks = chunks, tokens = tokens,
            memberships = memberships, contexts = contexts, reviews = reviews)
        val desired = case.desiredBytes ?: return base
        val baseBytes = ProfileTransferCodec.json.encodeToString(ProfileTransferCodec.canonical(base)).toByteArray(Charsets.UTF_8).size
        if (baseBytes >= desired) return base // Report an actual byte-cap rejection, never silently shrink rows.
        return base.copy(packs = listOf(packs.first().copy(title = packs.first().title + "0".repeat(desired - baseBytes))) + packs.drop(1))
    }
}
