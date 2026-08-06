package dev.ikna

import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.session.FLING_MIN_TRAVEL
import dev.ikna.ui.session.FLING_SPEED
import dev.ikna.ui.session.SWIPE_THRESHOLD
import dev.ikna.ui.session.decideRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gesture is the only way to answer a card, so this is the arithmetic that
 * decides what the app records. Every case here is a way it could silently lose
 * or invent an answer.
 */
class SwipeDecisionTest {

    @Test
    fun a_card_at_rest_is_not_an_answer() {
        assertNull(decideRating(0f, 0f))
    }

    @Test
    fun a_short_slow_drag_springs_back() {
        assertNull(decideRating(SWIPE_THRESHOLD - 1f, 0f))
        assertNull(decideRating(0f, SWIPE_THRESHOLD - 1f))
    }

    @Test
    fun four_directions_mean_four_answers() {
        assertEquals(Rating.AGAIN, decideRating(-SWIPE_THRESHOLD, 0f))
        assertEquals(Rating.GOOD, decideRating(SWIPE_THRESHOLD, 0f))
        assertEquals(Rating.EASY, decideRating(0f, -SWIPE_THRESHOLD))
        assertEquals(Rating.HARD, decideRating(0f, SWIPE_THRESHOLD))
    }

    @Test
    fun a_flick_counts_before_it_reaches_the_line() {
        // The old code ignored speed, so a fast flick that travelled a third of
        // the threshold sprang back and the answer was thrown away.
        assertEquals(
            Rating.GOOD,
            decideRating(SWIPE_THRESHOLD / 3f, 0f, velocityX = FLING_SPEED + 10f)
        )
        assertEquals(
            Rating.AGAIN,
            decideRating(-SWIPE_THRESHOLD / 3f, 0f, velocityX = -(FLING_SPEED + 10f))
        )
        assertEquals(
            Rating.EASY,
            decideRating(0f, -SWIPE_THRESHOLD / 3f, velocityY = -(FLING_SPEED + 10f))
        )
    }

    @Test
    fun speed_alone_is_not_enough() {
        // A tap with a pixel of jitter must never grade the card the user was
        // about to reveal.
        assertNull(decideRating(FLING_MIN_TRAVEL - 1f, 0f, velocityX = 5000f))
    }

    @Test
    fun a_flick_against_the_drag_is_ignored() {
        assertNull(decideRating(-40f, 0f, velocityX = FLING_SPEED + 500f))
    }

    @Test
    fun the_longer_axis_wins() {
        assertEquals(Rating.GOOD, decideRating(200f, 150f))
        assertEquals(Rating.HARD, decideRating(100f, 200f))
    }

    @Test
    fun the_threshold_itself_answers() {
        // Boundary, not "just past it": a gesture that stops exactly on the line
        // should commit rather than spring back.
        assertEquals(Rating.GOOD, decideRating(SWIPE_THRESHOLD, 0f, velocityX = 0f))
    }
}
