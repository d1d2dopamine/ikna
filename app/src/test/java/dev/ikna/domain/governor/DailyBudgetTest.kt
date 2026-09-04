package dev.ikna.domain.governor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules that keep a day's dose a day's dose.
 *
 * Today's plan is dropped whenever the content under it changes, and the deck
 * switch on the deck list does exactly that. Off, "today", on again used to be
 * three plans and three helpings of new material, which is both an inflated
 * counter today and a heavier queue for the rest of the week -- the one thing
 * the governor exists to prevent. These two functions are the whole guarantee,
 * so they are tested rather than trusted.
 */
class DailyBudgetTest {

    @Test
    fun `the first plan of the day is bound by nothing`() {
        assertEquals(4, ruledOnceToday(earlier = null, now = 4))
        assertEquals(0, ruledOnceToday(earlier = null, now = 0))
    }

    @Test
    fun `a rebuild cannot raise what the morning allowed`() {
        // The afternoon looks quiet -- cards were answered, headroom opened up --
        // and the governor would happily rule higher. The morning's number wins.
        assertEquals(3, ruledOnceToday(earlier = 3, now = 9))
    }

    @Test
    fun `a rebuild may lower it`() {
        // Accuracy fell, or it is now past the night cutoff. Caution travels.
        assertEquals(1, ruledOnceToday(earlier = 6, now = 1))
        assertEquals(0, ruledOnceToday(earlier = 6, now = 0))
    }

    @Test
    fun `a deck switched off and on again hands out nothing extra`() {
        // Morning: four allowed, four introduced. Then the switch is flicked
        // twice and the plan is built twice more.
        val budget = ruledOnceToday(earlier = 4, now = 4)
        assertEquals(0, dailyNewRoom(budget, introducedToday = 4))
        assertEquals(0, dailyNewRoom(budget, introducedToday = 4))
    }

    @Test
    fun `a rebuild still finishes a partly spent day`() {
        // Only three of the five fitted in the plan this morning, so the two
        // that were left are still owed -- a rebuild is a top-up, not a refusal.
        assertEquals(2, dailyNewRoom(5, introducedToday = 3))
    }

    @Test
    fun `an overspent day owes nothing`() {
        // Promotions count as new material too, and a promotion can take the
        // day past its budget. Negative room would read as credit.
        assertEquals(0, dailyNewRoom(2, introducedToday = 5))
        assertEquals(0, dailyNewRoom(0, introducedToday = 0))
    }
}
