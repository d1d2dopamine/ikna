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
 * Two answers, one axis.
 *
 * The card used to be throwable in four directions -- left "again", right "good",
 * up "easy", down "hard". Four is the number of grades FSRS accepts, not the
 * number of decisions a person can make in under a second about a phrase they
 * either recognised or did not. In practice the vertical pair produced two kinds
 * of damage. It asked for a judgement of *how* well the answer went, which is a
 * second question stacked on the first one and the exact kind of micro-decision
 * this app exists to remove. And it collided with the hand: a card is held near
 * the bottom of a phone, a thumb travelling right also travels up, and the axis
 * was picked by whichever displacement happened to be larger -- so "I knew it"
 * regularly landed as "that was easy", silently, with different scheduling.
 *
 * Now there is one axis and two outcomes: left is not known, right is known.
 * A gesture that is mostly vertical is not an answer at all; it springs back.
 * Both grades still exist in the data model, because the log is append-only and
 * every review ever recorded stays readable -- the interface just stopped asking
 * a question it could not ask well.
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
 * Travel that turns a pull into a look at the back of the card.
 *
 * Small on purpose: this is not a decision, it is the moment the hand admits it
 * wants to see the answer.
 */
const val PEEK_TRAVEL = 16f

/**
 * Left is not known, right is known. Nothing else is an answer.
 *
 * [y] is still an input, and it can only ever say no: a drag whose vertical
 * travel exceeds its horizontal travel is somebody moving their thumb, not
 * somebody grading a card, and grading it anyway is how the old four-way version
 * recorded answers nobody meant to give.
 */
fun decideRating(x: Float, y: Float = 0f, velocityX: Float = 0f): Rating? {
    if (abs(y) > abs(x)) return null
    return when {
        x <= -SWIPE_THRESHOLD -> Rating.AGAIN
        x >= SWIPE_THRESHOLD -> Rating.GOOD
        velocityX <= -FLING_SPEED && x <= -FLING_MIN_TRAVEL -> Rating.AGAIN
        velocityX >= FLING_SPEED && x >= FLING_MIN_TRAVEL -> Rating.GOOD
        else -> null
    }
}

/**
 * What would happen if the finger lifted right now.
 *
 * Used during the drag, for the tick under the thumb and for the label that
 * lights up. Deliberately velocity-free: it has to mean "you are past the line",
 * and a promise that fires because the hand is moving fast would be a lie -- the
 * card can still be dragged back.
 */
fun armedRating(x: Float, y: Float = 0f): Rating? = decideRating(x, y)
