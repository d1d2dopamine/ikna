package dev.ikna.domain.session

import dev.ikna.data.db.CardDao
import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ChunkDao
import dev.ikna.domain.governor.GovernorConfig
import dev.ikna.domain.governor.GovernorDecision
import kotlin.math.roundToInt

data class SessionPlan(
    val cards: List<SessionCard>,
    /**
     * What the UI is allowed to display. Clamped to capacity, so no screen in
     * this app can ever show a four-digit number of pending items. A visible
     * 487 is what ends a learning habit; the counter is therefore structurally
     * incapable of producing one.
     */
    val visibleCount: Int,
    val decision: GovernorDecision
)

class SessionBuilder(
    private val cardDao: CardDao,
    private val chunkDao: ChunkDao,
    private val config: GovernorConfig
) {

    suspend fun build(decision: GovernorDecision, now: Long): SessionPlan {
        val capacity = decision.capacity
        val amnestyQuota = decision.amnestyQuota

        val due = cardDao.dueCards(now, capacity - amnestyQuota)
        val amnesty = cardDao.amnestyCards(amnestyQuota)
        val all = (due + amnesty)

        val chunks = chunkDao.chunks(all.map { it.chunkId }.distinct()).associateBy { it.id }
        val amnestyIds = amnesty.map { it.chunkId to it.level }.toSet()

        val cards = all.mapNotNull { card ->
            val chunk = chunks[card.chunkId] ?: return@mapNotNull null
            SessionCard(
                card = card,
                chunk = chunk,
                level = Level.of(card.level),
                fromAmnesty = (card.chunkId to card.level) in amnestyIds
            )
        }.let { interleave(it) }

        return SessionPlan(
            cards = cards,
            visibleCount = cards.size.coerceAtMost(capacity),
            decision = decision
        )
    }

    /**
     * Amnesty cards are drip-fed rather than front-loaded, so returning after a
     * break never looks like punishment.
     */
    private fun interleave(cards: List<SessionCard>): List<SessionCard> {
        val (debt, fresh) = cards.partition { it.fromAmnesty }
        if (debt.isEmpty()) return fresh
        val step = (fresh.size.toDouble() / (debt.size + 1)).coerceAtLeast(1.0).roundToInt()
        val out = ArrayList<SessionCard>(cards.size)
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
