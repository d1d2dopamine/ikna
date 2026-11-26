package dev.ikna.domain.session

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import dev.ikna.domain.governor.GovernorReason
import kotlin.math.roundToInt

/**
 * What the session screen shows right now.
 *
 * `cards` is exactly the set of questions still owed: the day's plan minus
 * everything already answered, narrowed to one deck when the session was opened
 * from a deck. There is no second counter derived from anything else, because
 * the previous version had two — a display count and a queue length — and they
 * disagreed.
 *
 * The deck is a filter over the day's plan and never a plan of its own. The
 * governor measures one capacity for the whole day from real behaviour; giving
 * every deck its own budget would let three decks quietly authorise three times
 * the load, which is the exact failure this app exists to avoid.
 */
data class SessionPlan(
    val cards: List<SessionCard>,
    /** Size of today's plan across all decks, including any "ещё немного". */
    val plannedTotal: Int,
    /** Answers recorded today across all decks. The daily minimum reads this. */
    val answeredToday: Int,
    val reason: GovernorReason,
    /** When the next card comes back, for the empty state. */
    val nextDueAt: Long?,
    /** Null for "everything due today", otherwise the pack this session is limited to. */
    val deckId: String? = null,
    val deckTitle: String? = null,
    /** Cards in today's plan belonging to this session's scope. */
    val sessionTotal: Int = cards.size,
    /** How many of those are already answered. Drives the progress band. */
    val sessionDone: Int = 0,
    val forcedByDeveloper: Boolean = false,
    /** Ordinary answers eligible for the minimum; factual totals remain answeredToday. */
    val goalAnswersToday: Int = answeredToday
) {
    /**
     * Monotonic by construction: the plan is fixed for the day and answers only
     * ever remove items from it, so this number can only fall while the user
     * works. It grows only when the user explicitly asks for more.
     */
    val remaining: Int get() = cards.size
}

class SessionBuilder(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val config: GovernorConfig
) {

    /**
     * Chooses the questions for one day. Called once per day by the repository,
     * never on screen entry.
     *
     * [introduced] is what the governor authorised for today and the repository
     * has just created. It is reserved rather than merely allowed to compete: a
     * fresh card is written with `dueAt = now`, so in a "soonest first" query it
     * sorts behind every review that is already overdue and is the first thing a
     * capacity limit throws away. The day's new-material budget was being spent,
     * the counter recorded it, and the user was shown none of it.
     */
    suspend fun pickForDay(
        decision: GovernorDecision,
        now: Long,
        introduced: List<CardEntity> = emptyList(),
        suppressedChunkIds: Set<String> = emptySet()
    ): List<CardEntity> {
        val capacity = decision.capacity
        if (capacity <= 0) return emptyList()

        val reserved = introduced
            .filter { it.level == 0 && it.chunkId !in suppressedChunkIds }
            .take(capacity)
        val room = capacity - reserved.size
        if (room <= 0) return reserved

        val amnestyQuota = decision.amnestyQuota.coerceIn(0, room)
        // Exclude both reserved new cards and suppressed chunks BEFORE SQL LIMIT
        // so suppressed cards never steal review capacity slots.
        val exclude = (reserved.map { it.key } + suppressedChunkIds.map { "$it:0" })
            .distinct()
            .ifEmpty { listOf("") }

        val dueRoom = room - amnestyQuota
        val due =
            if (dueRoom > 0) cardDao.dueCardsExcluding(now, exclude, dueRoom) else emptyList()
        val amnesty =
            if (amnestyQuota > 0) cardDao.amnestyCardsExcluding(exclude, amnestyQuota)
            else emptyList()

        // Warm-up + interleaving (Storm, Bjork & Storm 2010; ADHD task initiation):
        // start with up to 2 familiar reviews when available, then spread new cards
        // evenly across the review queue to reduce proactive interference.
        val reviews = interleave(due + amnesty).take(room)
        return interleaveNewAndReviews(reserved, reviews)
    }

    /**
     * "Ещё немного": more repetitions, never new chunks, so a burst of
     * motivation today cannot become a heavier queue tomorrow.
     *
     * Three sources in order: what is due, what waits in amnesty, and — when
     * both are empty — the soonest cards from the future. The old version stopped
     * at the first two, which meant the button did nothing precisely when the day
     * was finished, which is the only time anyone presses it.
     */
    suspend fun pickExtra(
        exclude: List<String>,
        count: Int,
        now: Long,
        packId: String? = null,
        suppressedChunkIds: Set<String> = emptySet()
    ): List<CardEntity> {
        if (count <= 0) return emptyList()
        val safeExclude = (exclude + suppressedChunkIds.map { "$it:0" })
            .distinct()
            .ifEmpty { listOf("") }

        val due =
            if (packId == null) cardDao.dueCardsExcluding(now, safeExclude, count)
            else cardDao.dueCardsForPackExcluding(packId, now, safeExclude, count)
        if (due.size >= count) return due

        val room = count - due.size
        val amnesty =
            if (packId == null) cardDao.amnestyCardsExcluding(safeExclude, room)
            else cardDao.amnestyCardsForPackExcluding(packId, safeExclude, room)
        val repeats = due + amnesty
        if (repeats.size >= count) return repeats

        val taken = safeExclude + repeats.map { it.key }
        val left = count - repeats.size
        val ahead =
            if (packId == null) cardDao.upcomingCardsExcluding(now, taken, left)
            else cardDao.upcomingCardsForPackExcluding(packId, now, taken, left)
        return repeats + ahead
    }

    /**
     * Turns plan keys back into presentable cards, preserving plan order.
     * Cards that no longer exist (deck reset, undo of an introduction) are
     * dropped rather than faked.
     */
    suspend fun materialize(keys: List<String>): List<SessionCard> {
        if (keys.isEmpty()) return emptyList()
        val cards = cardDao.byKeys(keys).filter { it.level == 0 }.associateBy { it.key }
        val ordered = keys.mapNotNull { cards[it] }
        if (ordered.isEmpty()) return emptyList()

        val chunkIds = ordered.map { it.chunkId }.distinct()
        val chunks = chunkDao.chunks(chunkIds).associateBy { it.id }
        val matureChunkIds = ordered
            .filter { !it.isNew && it.stability >= ContextPolicy.DIVERSITY_MIN_STABILITY_DAYS }
            .map { it.chunkId }
            .distinct()
        val contextsByChunk = if (matureChunkIds.isEmpty()) {
            emptyMap()
        } else {
            matureChunkIds.chunked(400).flatMap { chunkDao.contextsForChunks(it) }.groupBy { it.chunkId }
        }
        return ordered.mapNotNull { card ->
            val baseChunk = chunks[card.chunkId] ?: return@mapNotNull null
            val chunk = ContextPolicy.resolveChunk(
                chunk = baseChunk,
                card = card,
                contexts = contextsByChunk[card.chunkId].orEmpty()
            )
            SessionCard(
                card = card,
                chunk = chunk,
                level = Level.of(card.level),
                fromAmnesty = card.inAmnesty
            )
        }
    }

    /**
     * Warm-up + interleaved ordering: open with up to two familiar reviews
     * when available, then weave newly introduced cards evenly among the
     * remaining reviews so new items are not clustered in one interference block.
     */
    fun interleaveNewAndReviews(
        newCards: List<CardEntity>,
        reviews: List<CardEntity>
    ): List<CardEntity> {
        if (newCards.isEmpty()) return reviews
        if (reviews.isEmpty()) return newCards

        val warmUpCount = minOf(2, reviews.size)
        val warmUp = reviews.take(warmUpCount)
        val restReviews = reviews.drop(warmUpCount)
        if (restReviews.isEmpty()) return warmUp + newCards

        val out = ArrayList<CardEntity>(newCards.size + reviews.size)
        out += warmUp
        val step = (restReviews.size.toDouble() / newCards.size).coerceAtLeast(1.0).roundToInt()
        var n = 0
        restReviews.forEachIndexed { idx, rev ->
            if (idx % step == 0 && n < newCards.size) {
                out += newCards[n++]
            }
            out += rev
        }
        while (n < newCards.size) out += newCards[n++]
        return out
    }

    /**
     * Amnesty cards are drip-fed rather than front-loaded, so returning after a
     * break never looks like punishment.
     */
    private fun interleave(cards: List<CardEntity>): List<CardEntity> {
        val (debt, fresh) = cards.partition { it.inAmnesty }
        if (debt.isEmpty()) return fresh
        val step = (fresh.size.toDouble() / (debt.size + 1)).coerceAtLeast(1.0).roundToInt()
        val out = ArrayList<CardEntity>(cards.size)
        var d = 0
        fresh.forEachIndexed { i, c ->
            out += c
            if ((i + 1) % step == 0 && d < debt.size) out += debt[d++]
        }
        while (d < debt.size) out += debt[d++]
        return out
    }

    /** The whole daily obligation. One card. Streaks cannot break. */
    fun dailyMinimum(): Int = config.dailyMinimumCards

}
