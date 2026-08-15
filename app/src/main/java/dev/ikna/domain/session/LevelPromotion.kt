package dev.ikna.domain.session

/**
 * When a chunk earns the next way of being asked.
 *
 * A chunk is met three times over its life -- recognition, cloze, production --
 * and each level is a separate card with its own schedule. Promotion is what
 * makes the same forty items feel different for months without the queue
 * growing, so it is deliberately generous: three weeks of stability and the next
 * level opens.
 *
 * The part that was missing is that **a promoted level is new material.** The
 * card it creates has never been shown, is written `isNew = true`, and lands
 * tomorrow. It was created inside the answer path regardless of what the
 * governor had allowed for the day and without being counted anywhere, so:
 *
 *  - On a day the governor had allowed zero new material -- late night, low
 *    accuracy, backlog over the limit, return mode after a break -- forty
 *    answers could still mint level-1 cards for tomorrow. The one number the
 *    whole app is built around was decided somewhere else.
 *  - `daily_stats.newIntroduced` never saw them, so the measured norm and the
 *    safety valve both read the day as lighter than it was, and tomorrow's
 *    capacity was computed from a day that had not really happened.
 *
 * Which is why the day's remaining budget is an argument here. Nothing is lost
 * by waiting: an item at three weeks of stability is still at three weeks
 * tomorrow, and the promotion happens on the next answer that has room for it.
 * If instead the item lapses in the meantime, it was not ready, and not
 * promoting it was the right answer all along.
 */
object LevelPromotion {

    /**
     * Stability, in days, at which the next level opens. Three weeks: long
     * enough that the item is genuinely held, short enough that a deck does not
     * stay in recognition forever.
     */
    const val STABILITY_DAYS = 21.0

    /**
     * @param newRoomToday what is left of the day's new-material budget: what
     *   the governor allowed minus what has already been introduced today.
     * @return the level to open, or null when nothing should be created.
     */
    fun nextLevel(level: Int, stability: Double, newRoomToday: Int): Int? = when {
        level >= Level.PRODUCTION.value -> null
        stability < STABILITY_DAYS -> null
        newRoomToday <= 0 -> null
        else -> level + 1
    }
}
