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
 * `cards` is exactly the set of questions still owed today: the day's plan
 * minus everything already answered. There is no second counter derived from
 * anything else, because the previous version had two — a display count and a
 * queue length — and they disagreed.
 */
data class SessionPlan(
    val cards: List<SessionCard>,
    /** Size of today's plan, including any "ещё немного" the user asked for. */
    val plannedTotal: Int,
    val answeredToday: Int,
    val reason: GovernorReason,
    /** When the next card comes back, for the empty state. */
    val nextDueAt: Long?
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
     */
    suspend fun pickForDay(decision: GovernorDecision, now: Long): List<CardEntity> {
        val capacity = decision.capacity
        val amnestyQuota = decision.amnestyQuota.coerceIn(0, capacity)

        val due = cardDao.dueCards(now, capacity - amnestyQuota)
        val amnesty = cardDao.amnestyCards(amnestyQuota)

        return interleave(due + amnesty).take(capacity)
    }

    /**
     * "Ещё немного": strictly more of what is already due or in amnesty.
     * Never introduces new chunks, so a burst of motivation today cannot become
     * a heavier queue tomorrow.
     */
    suspend fun pickExtra(exclude: List<String>, count: Int, now: Long): List<CardEntity> {
        if (count <= 0) return emptyList()
        val safeExclude = if (exclude.isEmpty()) listOf("") else exclude
        val due = cardDao.dueCardsExcluding(now, safeExclude, count)
        if (due.size >= count) return due
        val amnesty = cardDao.amnestyCardsExcluding(safeExclude, count - due.size)
        return due + amnesty
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
