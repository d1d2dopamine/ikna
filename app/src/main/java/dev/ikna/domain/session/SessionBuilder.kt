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
    val sessionDone: Int = 0
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
        introduced: List<CardEntity> = emptyList()
    ): List<CardEntity> {
        val capacity = decision.capacity
        if (capacity <= 0) return emptyList()

        val reserved = introduced.take(capacity)
        val room = capacity - reserved.size
        if (room <= 0) return reserved

        val amnestyQuota = decision.amnestyQuota.coerceIn(0, room)
        // Never offer the same question twice: the reserved cards are due now
        // and would otherwise come back out of the due query as well.
        val exclude = reserved.map { it.key }.ifEmpty { listOf("") }

        val dueRoom = room - amnestyQuota
        val due =
            if (dueRoom > 0) cardDao.dueCardsExcluding(now, exclude, dueRoom) else emptyList()
        val amnesty =
            if (amnestyQuota > 0) cardDao.amnestyCardsExcluding(exclude, amnestyQuota)
            else emptyList()

        // New chunks first, then the day's repetitions. The reserved cards are
        // prepended, so new material still lands while attention is freshest.
        return reserved + interleave(due + amnesty).take(room)
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
    suspend fun pickExtra(exclude: List<String>, count: Int, now: Long): List<CardEntity> {
        if (count <= 0) return emptyList()
        val safeExclude = if (exclude.isEmpty()) listOf("") else exclude

        val due = cardDao.dueCardsExcluding(now, safeExclude, count)
        if (due.size >= count) return due

        val amnesty = cardDao.amnestyCardsExcluding(safeExclude, count - due.size)
        val repeats = due + amnesty
        if (repeats.size >= count) return repeats

        val taken = safeExclude + repeats.map { it.key }
        val ahead = cardDao.upcomingCardsExcluding(now, taken, count - repeats.size)
        return repeats + ahead
    }

    /**
     * Turns plan keys back into presentable cards, preserving plan order.
     * Cards that no longer exist (deck reset, undo of an introduction) are
     * dropped rather than faked.
     */
    suspend fun materialize(keys: List<String>): List<SessionCard> {
        if (keys.isEmpty()) return emptyList()
        val cards = cardDao.byKeys(keys).associateBy { it.key }
        val ordered = keys.mapNotNull { cards[it] }
        if (ordered.isEmpty()) return emptyList()

        val chunks = chunkDao.chunks(ordered.map { it.chunkId }.distinct()).associateBy { it.id }
        return ordered.mapNotNull { card ->
            val chunk = chunks[card.chunkId] ?: return@mapNotNull null
            SessionCard(
                card = card,
                chunk = chunk,
                level = Level.of(card.level),
                fromAmnesty = card.inAmnesty
            )
        }
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

    fun nextLevelFor(card: CardEntity): Int? = when {
        card.level < Level.PRODUCTION.value && card.stability >= 21.0 -> card.level + 1
        else -> null
    }

}
