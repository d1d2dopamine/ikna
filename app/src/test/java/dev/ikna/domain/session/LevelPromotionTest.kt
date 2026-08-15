package dev.ikna.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A promotion creates a card the user has never been asked, so it spends the
 * same budget an introduction spends. These tests pin that down, because the
 * rule is easy to lose again: the promotion happens in the answer path, far away
 * from the once-a-day place where new material is normally decided.
 */
class LevelPromotionTest {

    @Test
    fun `a solid item opens the next level when the day has room`() {
        assertEquals(1, LevelPromotion.nextLevel(level = 0, stability = 21.0, newRoomToday = 3))
        assertEquals(2, LevelPromotion.nextLevel(level = 1, stability = 40.0, newRoomToday = 1))
    }

    @Test
    fun `no new material today means no promotion today`() {
        assertNull(
            "The governor is the only thing allowed to decide how much new " +
                "material arrives, and a promoted level is new material.",
            LevelPromotion.nextLevel(level = 0, stability = 100.0, newRoomToday = 0)
        )
    }

    @Test
    fun `an item that is not yet solid is not promoted`() {
        assertNull(LevelPromotion.nextLevel(level = 0, stability = 20.9, newRoomToday = 5))
    }

    @Test
    fun `production is the last level`() {
        assertNull(
            LevelPromotion.nextLevel(
                level = Level.PRODUCTION.value,
                stability = 500.0,
                newRoomToday = 10
            )
        )
    }

    @Test
    fun `waiting for room costs nothing`() {
        // The same item, one day later, with the budget available again.
        val today = LevelPromotion.nextLevel(level = 0, stability = 30.0, newRoomToday = 0)
        val tomorrow = LevelPromotion.nextLevel(level = 0, stability = 30.0, newRoomToday = 2)
        assertNull(today)
        assertEquals(1, tomorrow)
    }
}
