package dev.ikna.data.repo

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.HistoryBoundaryEntity
import dev.ikna.data.db.IknaDatabase
import dev.ikna.data.db.PlanDao
import dev.ikna.data.db.ReviewDao
import dev.ikna.data.db.inTransaction
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.session.BrowsePolicy
import dev.ikna.domain.session.OptionalHardBatch
import dev.ikna.domain.session.ReviewPurpose
import dev.ikna.domain.session.ReviewSignals
import dev.ikna.domain.session.SessionBuilder
import dev.ikna.domain.session.SessionCard
import dev.ikna.domain.session.classicOnly
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns ephemeral post-plan optional review batches for repeatedly lapsed cards.
 * Runs under [LearningRepository]'s write lock and delegates actual answer/undo
 * persistence back to the repository so journal locking stays unified.
 */
internal class OptionalHardReviewService(
    private val db: IknaDatabase,
    private val cardDao: CardDao,
    private val reviewDao: ReviewDao,
    private val planDao: PlanDao,
    private val writeLock: Mutex,
    private val leechMinLapses: Int,
    private val builder: () -> SessionBuilder,
    private val dayKey: (Long) -> String,
    private val startOfDay: (Long) -> Long,
    private val activeHistoryCutoff: suspend () -> Long,
    private val suppressedNow: suspend () -> Set<String>,
    private val checkHistoryWritable: suspend () -> Unit,
    private val syncHistoryBoundary: () -> Long?,
    private val historyBoundaryToken: () -> String?,
    private val answerLocked: suspend (SessionCard, Rating, Long, Long, ReviewSignals, String?) -> Long,
    private val undoLastLocked: suspend (Long, Long?) -> String?
) {
    private data class OptionalHardState(
        val batch: OptionalHardBatch,
        val dayStart: Long,
        val boundary: HistoryBoundaryEntity?,
        val configuredCutoff: Long?,
        val configuredToken: String?,
        val committed: MutableList<Pair<String, Long>> = mutableListOf()
    )

    private var state: OptionalHardState? = null

    fun clear() {
        state = null
    }

    suspend fun available(deckId: String?, now: Long): Boolean = writeLock.withLock {
        db.inTransaction {
            isComplete(now) && candidates(deckId, 1).isNotEmpty()
        }
    }

    suspend fun start(deckId: String?, now: Long): OptionalHardBatch? = writeLock.withLock {
        checkHistoryWritable()
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val next = db.inTransaction {
            checkHistoryWritable()
            if (!isComplete(now)) return@inTransaction null
            val cards = builder().materialize(candidates(deckId, 5).map { it.key })
            if (cards.isEmpty()) return@inTransaction null
            OptionalHardState(
                batch = OptionalHardBatch(java.util.UUID.randomUUID().toString(), dayKey(now), deckId, cards),
                dayStart = startOfDay(now),
                boundary = db.historyBoundaryDao().current(),
                configuredCutoff = syncHistoryBoundary(),
                configuredToken = historyBoundaryToken()
            ).also { caller.ensureActive() }
        }
        state = next
        next?.batch
    }

    suspend fun cancel(batch: OptionalHardBatch) = writeLock.withLock {
        if (state?.batch?.token == batch.token) state = null
    }

    suspend fun answer(
        batch: OptionalHardBatch,
        card: SessionCard,
        rating: Rating,
        durationMs: Long,
        now: Long,
        signals: ReviewSignals
    ) = writeLock.withLock {
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val active = requireState(batch)
        val key = card.card.key
        check(active.committed.none { it.first == key }) { "Optional review already answered" }
        val id = db.inTransaction {
            val fresh = validate(active, key, now)
            answerLocked(fresh, rating, durationMs, now, signals, ReviewPurpose.OPTIONAL_HARD)
                .also { caller.ensureActive() }
        }
        active.committed += key to id
    }

    suspend fun undo(batch: OptionalHardBatch, now: Long): SessionCard? = writeLock.withLock {
        val active = requireState(batch)
        val previous = active.committed.lastOrNull() ?: return@withLock null
        val caller = currentCoroutineContext()
        caller.ensureActive()
        val restored = db.inTransaction {
            validate(active, previous.first, now)
            val key = undoLastLocked(now, previous.second) ?: return@inTransaction null
            builder().materialize(listOf(key)).singleOrNull().also { caller.ensureActive() }
        }
        if (restored != null) active.committed.removeAt(active.committed.lastIndex)
        restored
    }

    private fun requireState(batch: OptionalHardBatch): OptionalHardState =
        checkNotNull(state?.takeIf { it.batch == batch }) { "Optional review batch is no longer valid" }

    private suspend fun isComplete(now: Long): Boolean {
        val plan = planDao.plan(dayKey(now))?.classicOnly() ?: return false
        val required = BrowsePolicy.requiredIds(plan.ids, plan.extraRequested)
        val cutoff = activeHistoryCutoff()
        return required.isNotEmpty() && BrowsePolicy.requiredComplete(
            required,
            reviewDao.answeredKeysSinceAfter(startOfDay(now), cutoff).toSet()
        )
    }

    internal suspend fun candidates(deckId: String?, limit: Int): List<CardEntity> =
        cardDao.optionalHardCards(
            deckId,
            suppressedNow().toList().ifEmpty { listOf("") },
            leechMinLapses,
            limit
        )

    private suspend fun validate(active: OptionalHardState, key: String, now: Long): SessionCard {
        checkHistoryWritable()
        check(
            active.batch.day == dayKey(now) &&
                active.dayStart == startOfDay(now) &&
                active.boundary == db.historyBoundaryDao().current() &&
                active.configuredCutoff == syncHistoryBoundary() &&
                active.configuredToken == historyBoundaryToken() &&
                isComplete(now)
        ) { "Optional review batch is no longer valid" }
        val original = active.batch.cards.singleOrNull { it.card.key == key }
        check(
            original != null &&
                original.chunk.id !in suppressedNow() &&
                cardDao.optionalHardEligible(original.chunk.id, active.batch.deckId, leechMinLapses)
        ) { "Optional review target is no longer eligible" }
        val fresh = builder().materialize(listOf(key)).singleOrNull()
        check(fresh != null && fresh.chunk == original.chunk) { "Optional review target changed" }
        return fresh
    }
}
