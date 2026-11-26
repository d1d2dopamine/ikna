package dev.ikna.data.repo

import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.inTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.DailyStatEntity
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.ReviewEntity
import dev.ikna.data.db.StatsDao
import dev.ikna.data.export.ReviewRecord
import dev.ikna.domain.fsrs.DAY_MS
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.time.DayBoundary

data class RestoreResult(val imported: Int, val skipped: Int, val replayed: Int)

/**
 * Restore from an exported review log.
 *
 * This is the reason the log is append-only. Card schedules, the word layer and
 * every statistic are derived values: given the answers back, they can be
 * recomputed exactly by replaying them through the same scheduler. So a restore
 * is not "copying a backup over the app", it is replaying a history — which
 * also means a future change to the algorithm can re-derive everything from the
 * same file.
 *
 * The one thing in the file that must NOT be taken literally is the row id. Ids
 * belong to the database that issued them, and a restore is normally into a
 * different one: a new phone, or an install that has been used for a week
 * before the file arrived. Importing them verbatim either collides with a row
 * that already exists or, worse, quietly attaches an old retraction to whatever
 * unrelated answer happens to hold that number now. Rows are therefore inserted
 * unnumbered, and the undo trail is re-pointed through the ids SQLite hands
 * back.
 */
class RestoreRepository(
    private val db: IknaDatabase,
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val statsDao: StatsDao,
    private val planDao: PlanDao,
    private val components: ComponentRepository,
    private val scheduler: Scheduler,
    private val config: GovernorConfig,
    private val beforeExternalRestore: suspend () -> Long = { 0L },
    private val afterExternalRestore: suspend (Long) -> Unit = {}
) {

    val incrementalJournal by lazy { IncrementalJournalRepository(db, this) }

    val profileTransfer: ProfileTransferRepository by lazy { ProfileTransferRepository(db, this) }

    /** Local archive boundary installed by the sync controller, also used by maintenance replay. */
    var historyBoundary: (() -> Long?)? = null

    /** Production wiring serializes the whole merge with local plan/answer writes.
     * Lock order is learning mutex, then Room; callbacks run after this guard returns. */
    var journalMergeGuard: suspend (suspend () -> RestoreResult) -> RestoreResult = { it() }

    suspend fun persistedHistoryBoundary() = db.historyBoundaryDao().current()

    internal suspend fun validateSyncBoundary(afterLocalId: Long, boundaryToken: String?) {
        val marker = db.historyBoundaryDao().current()
        require(marker?.token == boundaryToken && (marker == null || marker.afterLocalId == afterLocalId)) {
            "History reset marker differs from pairing state; reset progress before starting a new sync history"
        }
    }

    /** Consistent full snapshot, including undo targets; never a delta protocol. */
    suspend fun maxJournalId(): Long {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction { reviewDao.maxId().also { caller.ensureActive() } }
    }

    suspend fun snapshotJournal(afterLocalId: Long = 0L, boundaryToken: String? = null): List<ReviewRecord> {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction {
            caller.ensureActive()
            validateSyncBoundary(afterLocalId, boundaryToken)
            val rows = reviewDao.afterId(afterLocalId)
            caller.ensureActive()
            dev.ikna.data.export.JournalIdentity.normalize(rows.map {
                caller.ensureActive()
                ReviewRecord.of(it)
            }).sortedWith(compareBy<ReviewRecord> { it.ts }.thenBy { it.eventId })
                .also { caller.ensureActive() }
        }
    }

    /** Strict, additive union. Any invalid payload rolls back history AND derived writes. */
    suspend fun mergeJournal(
        records: List<ReviewRecord>,
        afterLocalId: Long = 0L,
        boundaryToken: String? = null,
        skipMissingTargets: Boolean = false
    ): RestoreResult =
        journalMergeGuard { mergeJournalLocked(records, afterLocalId, boundaryToken, skipMissingTargets) }

    internal suspend fun mergeJournalLocked(
        records: List<ReviewRecord>,
        afterLocalId: Long,
        boundaryToken: String?,
        skipMissingTargets: Boolean = false
    ): RestoreResult {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val incoming = validateJournalRecords(records)
        return db.inTransaction {
            caller.ensureActive()
            validateSyncBoundary(afterLocalId, boundaryToken)
            val localRows = reviewDao.afterId(afterLocalId)
            val local = dev.ikna.data.export.JournalIdentity.normalize(localRows.map { ReviewRecord.of(it) })
            val filteredIncoming = if (!skipMissingTargets) incoming else {
                val kept = incoming.filter { r ->
                    caller.ensureActive()
                    db.chunkDao().chunk(r.chunkId) != null
                }
                val availableIds = (local.mapNotNull { it.eventId } + kept.mapNotNull { it.eventId }).toSet()
                kept.filter { r -> r.undoEventId == null || r.undoEventId in availableIds }
            }
            val union = LinkedHashMap<String, ReviewRecord>()
            for (r in local + filteredIncoming) {
                val old = union.putIfAbsent(r.eventId!!, r)
                require(old == null || old == r) { "Conflicting payload for event ${r.eventId}" }
            }
            require(union.size <= 1_000_000) { "Journal exceeds supported event count" }
            var totalDuration = 0L
            for (r in union.values) {
                totalDuration = Math.addExact(totalDuration, r.durationMs)
                caller.ensureActive()
                if (!skipMissingTargets) {
                    require(db.chunkDao().chunk(r.chunkId) != null) { "Install the matching deck before syncing target ${r.chunkId}" }
                }
                r.undoEventId?.let { target ->
                    val answer = union[target]
                    require(answer != null && answer.undoEventId == null && answer.chunkId == r.chunkId &&
                        answer.level == r.level && answer.purpose == r.purpose) { "Missing or invalid undo target" }
                }
            }
            val ids = local.zip(localRows).associate { it.first.eventId!! to it.second.id }.toMutableMap()
            val additions = union.values.filter { it.eventId !in ids }
                .sortedWith(compareBy<ReviewRecord> { it.ts }.thenBy { it.eventId })
            for (r in additions.filter { it.undoEventId == null }) {
                caller.ensureActive()
                ids[r.eventId!!] = reviewDao.insert(r.toEntity(undoOf = null))
            }
            for (r in additions.filter { it.undoEventId != null }) {
                caller.ensureActive()
                ids[r.eventId!!] = reviewDao.insert(r.toEntity(undoOf = ids.getValue(r.undoEventId!!)))
            }
            val replayed = if (additions.isEmpty()) 0 else {
                val count = replayFromLogBody(caller, sync = true, afterLocalId = afterLocalId)
                // Old queue entries can refer to cards made ineligible by the merge.
                // Completion and admission budgets live in stats and remain untouched.
                planDao.clear()
                count
            }
            caller.ensureActive()
            RestoreResult(additions.size, records.size - additions.size, replayed)
        }
    }

    internal fun validateJournalRecords(records: List<ReviewRecord>): List<ReviewRecord> {
        require(records.all { it.eventId != null }) { "Sync requires portable event identities" }
        val incoming = dev.ikna.data.export.JournalIdentity.normalize(records)
        incoming.forEach { r ->
            require(!r.synthetic && r.chunkId.isNotBlank() && r.level in 0..2 && r.ts in 0..253402300799999L &&
                r.durationMs >= 0 && r.elapsedDays.isFinite() && r.elapsedDays >= 0 &&
                r.stabilityBefore.isFinite() && r.stabilityAfter.isFinite() &&
                r.difficultyBefore.isFinite() && r.difficultyAfter.isFinite() &&
                r.stabilityBefore in 0.0..1.0e9 && r.stabilityAfter in 0.0..1.0e9 &&
                r.difficultyBefore in 1.0..10.0 && r.difficultyAfter in 1.0..10.0 &&
                (r.prevStability == null || (r.prevStability.isFinite() && r.prevStability in 0.0..1.0e9)) &&
                (r.prevDifficulty == null || (r.prevDifficulty.isFinite() && r.prevDifficulty in 1.0..10.0)) &&
                (r.prevReps == null || r.prevReps in 0..1_000_000) &&
                (r.prevLapses == null || r.prevLapses in 0..1_000_000) &&
                (r.prevLastReviewAt == null || r.prevLastReviewAt in 0..253402300799999L) &&
                (r.prevDueAt == null || r.prevDueAt in 0..253402300799999L) &&
                (r.swipeVelocityX == null || r.swipeVelocityX.isFinite()) &&
                (r.latencyMs == null || r.latencyMs >= 0)) { "Invalid journal observation" }
            require(if (r.undoEventId == null) r.rating in 1..4 else r.rating == 0) { "Invalid journal rating" }
            require(r.inputRating == null || r.inputRating in 1..4) { "Invalid original rating" }
            require(r.gradingVersion == null || r.gradingVersion == 1) { "Unsupported grading version" }
            require(r.gradingVersion != 1 || (r.inputRating == 3 && r.rating in listOf(2, 4))) { "Invalid derived grading" }
            r.fsrsParameters?.let(dev.ikna.domain.fsrs.FsrsSnapshotCodec::decode)
        }
        return incoming
    }

    class ExternalRestorePreparation internal constructor(internal val owner: RestoreRepository, internal val generation: Long)

    /** Call before acquiring an outer Room transaction (e.g. atomic Anki import). */
    suspend fun prepareExternalRestore(): ExternalRestorePreparation {
        val generation = beforeExternalRestore()
        return ExternalRestorePreparation(this, generation)
    }

    /** Finalize only after the OUTERMOST content/history transaction has committed. */
    suspend fun completeExternalRestore(preparation: ExternalRestorePreparation) {
        require(preparation.owner === this) { "Restore preparation belongs to another repository" }
        afterExternalRestore(preparation.generation)
    }

    suspend fun restoreFromJsonl(text: String, preparation: ExternalRestorePreparation? = null): RestoreResult {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        var skipped = 0
        val records = ArrayList<ReviewRecord>()
        for (line in text.lineSequence()) {
            caller.ensureActive()
            if (line.isBlank()) continue
            // Unlike a malformed old line, an unknown accounting purpose must not be
            // silently discarded or reinterpreted before the all-or-nothing import.
            val parsed = runCatching { ReviewRecord.json.parseToJsonElement(line) }.getOrNull()
            if (parsed is kotlinx.serialization.json.JsonObject && "purpose" in parsed) {
                val purpose = parsed["purpose"]
                require(purpose == kotlinx.serialization.json.JsonNull ||
                    (purpose is kotlinx.serialization.json.JsonPrimitive && purpose.isString &&
                        purpose.content == dev.ikna.domain.session.ReviewPurpose.OPTIONAL_HARD)) {
                    "Unsupported review purpose"
                }
            }
            val rec = runCatching {
                ReviewRecord.json.decodeFromString(ReviewRecord.serializer(), line)
            }.getOrNull()
            if (rec == null || rec.synthetic) {
                skipped++
                continue
            }
            // Invalid activity cannot enter a new import or trigger pairing/reset
            // preparation. Historical nonnegative durations stay lossless, even
            // when longer than the current new-answer recording limit.
            require(rec.durationMs >= 0L) { "Invalid review duration" }
            require(rec.gradingVersion == null || rec.gradingVersion == 1) {
                "Unsupported derived grading version: ${rec.gradingVersion}"
            }
            require(rec.gradingVersion != 1 || (rec.inputRating == 3 && rec.rating in listOf(2, 4))) {
                "Invalid derived grading record"
            }
            rec.fsrsParameters?.let(dev.ikna.domain.fsrs.FsrsSnapshotCodec::decode)
            records += rec
        }

        if (records.isEmpty()) return RestoreResult(imported = 0, skipped = skipped, replayed = 0)

        val prepared = preparation ?: prepareExternalRestore()
        require(prepared.owner === this) { "Restore preparation belongs to another repository" }
        caller.ensureActive()
        // Parse, validate and sort before locking the writer. History and every
        // derived write below share the same transaction, including key lookup.
        val normalized = dev.ikna.data.export.JournalIdentity.normalize(records)
        val sorted = records.zip(normalized).map { (raw, stable) ->
            stable.copy(id = raw.id, undoOf = raw.undoOf)
        }.sortedWith(compareBy<ReviewRecord> { it.ts }.thenBy { it.eventId })
        val result = db.inTransaction {
            caller.ensureActive()
            restoreValidated(sorted, skipped, caller).also { caller.ensureActive() }
        }
        if (preparation == null) completeExternalRestore(prepared)
        return result
    }

    private suspend fun restoreValidated(
        sorted: List<ReviewRecord>, initiallySkipped: Int, caller: CoroutineContext
    ): RestoreResult {
        var skipped = initiallySkipped
        val localRows = reviewDao.all()
        val localStable = dev.ikna.data.export.JournalIdentity.normalize(localRows.map { ReviewRecord.of(it) })
        val portable = localStable.associateBy { it.eventId!! }.toMutableMap()
        for (r in sorted) {
            val canonical = dev.ikna.data.export.JournalIdentity.canonical(r)
            val previous = portable.putIfAbsent(r.eventId!!, canonical)
            require(previous == null || previous == canonical) { "Conflicting event payload" }
        }
        val known = localRows.zip(localStable).associate { it.second.eventId!! to it.first.id }.toMutableMap()

        // Id in the file -> id here. This is what makes `undoOf` survive.
        val remap = HashMap<Long, Long>()

        // Pass one: the answers. They have to land before the retractions,
        // because a retraction is only meaningful once the row it points at
        // exists and has a number.
        val staged = HashSet<String>()
        val answers = ArrayList<Pair<ReviewRecord, ReviewEntity>>()
        for (rec in sorted) {
            caller.ensureActive()
            if (rec.undoOf != null || rec.undoEventId != null) continue
            val existing = known[rec.signature]
            if (existing != null) {
                // Already here. Still worth remembering, because a retraction
                // later in the same file may point at it.
                if (rec.id != 0L) remap[rec.id] = existing
                skipped++
                continue
            }
            if (!staged.add(rec.signature)) {
                skipped++
                continue
            }
            answers += rec to rec.toEntity()
        }
        var imported = insertRecords(answers, known, remap, caller)

        // Pass two: the retractions, each re-pointed at the local id of the
        // answer it undoes. One whose target cannot be resolved is dropped
        // rather than imported dangling: a retraction of nothing would sit in
        // the log forever and hide a row that no longer exists.
        val stagedUndo = HashSet<String>()
        val retractions = ArrayList<Pair<ReviewRecord, ReviewEntity>>()
        for (rec in sorted) {
            caller.ensureActive()
            if (rec.undoOf == null && rec.undoEventId == null) continue
            val target = rec.undoOf
            if (known.containsKey(rec.signature) || !stagedUndo.add(rec.signature)) {
                skipped++
                continue
            }
            val here = rec.undoEventId?.let { known[it] } ?: remap[target]
            if (here == null) {
                skipped++
                continue
            }
            require(portable[rec.undoEventId]?.purpose == rec.purpose) { "Undo purpose differs from its answer" }
            retractions += rec to rec.toEntity(undoOf = here)
        }
        imported += insertRecords(retractions, known, remap, caller)

        db.incrementalJournalDao().invalidate()
        val replayed = replayFromLogBody(caller)
        if (db.historyBoundaryDao().current() != null) db.historyBoundaryDao().put(
            dev.ikna.data.db.HistoryBoundaryEntity(afterLocalId = 0, token = java.util.UUID.randomUUID().toString()))
        return RestoreResult(imported = imported, skipped = skipped, replayed = replayed)
    }

    /**
     * Inserts a batch and records the ids SQLite assigned, in order.
     *
     * Bounded batches keep DAO work and cancellation latency manageable while
     * the enclosing transaction protects the entire restore.
     */
    private suspend fun insertRecords(
        pending: List<Pair<ReviewRecord, ReviewEntity>>,
        known: MutableMap<String, Long>,
        remap: MutableMap<Long, Long>,
        caller: CoroutineContext
    ): Int {
        var imported = 0
        for (part in pending.chunked(INSERT_BATCH)) {
            caller.ensureActive()
            val ids = reviewDao.insertAll(part.map { it.second })
            caller.ensureActive()
            for ((i, pair) in part.withIndex()) {
                val newId = ids.getOrNull(i) ?: continue
                if (newId <= 0L) continue
                val rec = pair.first
                known[rec.signature] = newId
                if (rec.id != 0L) remap[rec.id] = newId
                imported++
            }
        }
        return imported
    }

    /**
     * Rebuilds every derived table by replaying the log through the scheduler.
     * Retracted answers are already filtered out by the DAO, so an undo made
     * months ago stays undone.
     *
     * Everything is accumulated in memory and written once at the end. The old
     * version read and wrote one card per answer, which on a real log is two
     * SQLite round trips per row for a table small enough to hold whole — and
     * this runs on the main path of every restore.
     */
    suspend fun replayFromLog(): Int {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        return db.inTransaction {
            caller.ensureActive()
            val configuredBoundary = historyBoundary?.invoke()
            val cutoff = db.historyBoundaryDao().current()?.afterLocalId ?: configuredBoundary
            replayFromLogBody(caller, sync = cutoff != null,
                afterLocalId = cutoff ?: 0L).also { caller.ensureActive() }
        }
    }

    suspend fun replaySyncJournal(afterLocalId: Long): Int {
        val caller = currentCoroutineContext()
        return db.inTransaction { replayFromLogBody(caller, sync = true,
            afterLocalId = db.historyBoundaryDao().current()?.afterLocalId ?: afterLocalId) }
    }

    /** Runs only inside the owning transaction; Room's job is not the caller's. */
    internal suspend fun replayFromLogBody(caller: CoroutineContext, sync: Boolean = false, afterLocalId: Long = 0L, journalStreamId: String? = null): Int {
        caller.ensureActive()
        val localCards = if (sync) cardDao.all().associateBy { it.key } else emptyMap()
        val localDays = if (sync) statsDao.lastDays(Int.MAX_VALUE).associateBy { it.day } else emptyMap()
        if (!sync) cardDao.clear()
        caller.ensureActive()
        statsDao.clear()
        caller.ensureActive()
        if (!sync) planDao.clear()
        caller.ensureActive()

        val rawAnswers = reviewDao.answersAfterId(afterLocalId)
        val identities = if (sync && journalStreamId != null) {
            db.incrementalJournalDao().replayIdentities(journalStreamId, afterLocalId).associate { row ->
                caller.ensureActive()
                if (row.rawEventId != null && row.indexedEventId != null && row.rawEventId != row.indexedEventId)
                    throw dev.ikna.data.export.JournalReconciliationRequiredException()
                row.id to (row.indexedEventId ?: row.rawEventId
                    ?: throw dev.ikna.data.export.JournalReconciliationRequiredException())
            }
        } else if (sync) reviewDao.afterId(afterLocalId).let { rows ->
            rows.zip(dev.ikna.data.export.JournalIdentity.normalize(rows.map { ReviewRecord.of(it) }))
                .associate { it.first.id to it.second.eventId!! }
        } else emptyMap()
        val answers = if (sync) rawAnswers.sortedWith(compareBy<ReviewEntity> { it.ts }.thenBy { identities[it.id] }) else rawAnswers
        val replayScheduler = if (sync) Scheduler(dayStartHour = config.dayStartHour) else scheduler
        val syncOriginals = if (sync) reviewDao.afterId(afterLocalId) else emptyList()
        val replayed = JournalReplayEngine.replayFullHistory(
            answers = answers,
            scheduler = replayScheduler,
            dayStartHour = config.dayStartHour,
            sync = sync,
            syncOriginals = syncOriginals,
            syncIdentities = identities,
            localCards = localCards,
            localDays = localDays,
            onStep = { caller.ensureActive() }
        )
        val cards = replayed.cards
        val days = replayed.days
        cards.values.chunked(WRITE_BATCH).forEach {
            caller.ensureActive()
            cardDao.upsertAll(it)
        }
        days.values.chunked(WRITE_BATCH).forEach {
            caller.ensureActive()
            statsDao.upsertAll(it)
        }
        caller.ensureActive()

        // Every card above was written visible, because the log records when an
        // answer happened and never records what was hidden at the time. A file
        // that is two weeks old therefore lands as a queue with two weeks of
        // overdue cards in it at once — the avalanche the amnesty pool exists to
        // prevent, arriving on the first screen after a restore.
        //
        // The daily plan applies the same rule and would eventually apply it
        // here too, but "eventually" is whichever screen opens first, and the
        // deck list and the statistics screen both read cards. So the restored
        // database is made consistent here, once, before anything reads it.
        if (!sync) cardDao.moveOverdueToAmnesty(
            System.currentTimeMillis() - config.amnestyAfterDays * DAY_MS
        )

        caller.ensureActive()
        components.rebuildFromReviews(answers) { caller.ensureActive() }
        caller.ensureActive()
        return answers.size
    }


    private companion object {
        const val INSERT_BATCH = 500
        const val WRITE_BATCH = 500
    }
}
