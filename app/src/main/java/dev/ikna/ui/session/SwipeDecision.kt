package dev.ikna.ui.session

import dev.ikna.domain.fsrs.Rating
import kotlin.math.abs

/*
 * What the gesture meant.
 *
 * Pulled out of the composable so it can be tested without a screen, and because
 * it is the single most consequential piece of arithmetic in the app: it decides
 * what happens to a card, and getting it wrong either loses answers or records
 * ones nobody gave.
 *
 * Two ways to answer, not one. Distance means "I dragged it far enough", which is
 * the deliberate version. Speed means "I flicked it", which is what a hand does
 * when it already knows the answer and does not want to spend a whole gesture on
 * it — and the old code ignored speed entirely, so a fast flick that travelled
 * 100px sprang back to centre and the answer was silently thrown away. That is
 * the single most annoying thing an app can do to someone answering quickly, and
 * answering quickly is the entire point here.
 */

/** How far a deliberate drag has to travel. */
const val SWIPE_THRESHOLD = 140f

/** How fast a flick has to be, in pixels per second, to count on its own. */
const val FLING_SPEED = 900f

/**
 * A flick still has to move the card.
 *
 * Without this a fast tap with a pixel of jitter registers as an answer, and the
 * card you meant to reveal is graded instead.
 */
const val FLING_MIN_TRAVEL = 24f

/**
 * Left is lost, right is kept, up is easy, down is hard.
 *
 * The axis is chosen by displacement rather than by velocity: the card has
 * visibly gone one way by the time the finger lifts, and grading it in the other
 * direction because the last few milliseconds went sideways would be indefensible
 * from the user's chair.
 */
fun decideRating(
    x: Float,
    y: Float,
    velocityX: Float = 0f,
    velocityY: Float = 0f
): Rating? {
    val horizontal = abs(x) >= abs(y)
    return if (horizontal) {
        when {
            x <= -SWIPE_THRESHOLD -> Rating.AGAIN
            x >= SWIPE_THRESHOLD -> Rating.GOOD
            velocityX <= -FLING_SPEED && x <= -FLING_MIN_TRAVEL -> Rating.AGAIN
            velocityX >= FLING_SPEED && x >= FLING_MIN_TRAVEL -> Rating.GOOD
            else -> null
        }
    } else {
        when {
            y <= -SWIPE_THRESHOLD -> Rating.EASY
            y >= SWIPE_THRESHOLD -> Rating.HARD
            velocityY <= -FLING_SPEED && y <= -FLING_MIN_TRAVEL -> Rating.EASY
            velocityY >= FLING_SPEED && y >= FLING_MIN_TRAVEL -> Rating.HARD
            else -> null
        }
    }
}

/**
 * What would happen if the finger lifted right now.
 *
 * Used during the drag, for the tick under the thumb. Deliberately velocity-free:
 * the tick has to mean "you are past the line", and a tick that fires because the
 * hand is moving fast would be a lie — the card can still be dragged back.
 */
fun armedRating(x: Float, y: Float): Rating? = decideRating(x, y)
