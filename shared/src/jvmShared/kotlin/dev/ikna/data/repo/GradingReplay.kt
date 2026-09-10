package dev.ikna.data.repo

import dev.ikna.data.db.CardEntity
import dev.ikna.data.db.ReviewEntity
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.fsrs.ScheduleResult
import dev.ikna.domain.fsrs.Scheduler
import dev.ikna.domain.grading.DERIVED_GRADING_VERSION
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.grading.PEEK_REQUIRED
import dev.ikna.domain.grading.TimingSample
import dev.ikna.domain.grading.TimingWindow
import dev.ikna.domain.session.ReviewSignals

/** A derived HARD is a correct input, not a failed answer to the governor. */
val ReviewEntity.outcomeRating: Int
    get() = if (gradingVersion == DERIVED_GRADING_VERSION) inputRating ?: rating else rating

fun ReviewEntity.observations() = ReviewSignals(
    latencyMs = latencyMs,
    swipeVelocityX = swipeVelocityX,
    peeked = peeked,
    timingDiscardReason = timingDiscardReason,
    inputMethod = inputMethod,
    peekSemantics = peekSemantics
)

/** Input is already undo-filtered and chronological. Latest 200 per modality wins. */
fun gradingWindowFromReviews(
    answers: List<ReviewEntity>,
    inputMethod: String = INPUT_SWIPE
): TimingWindow = TimingWindow().also { window ->
    for (row in answers) {
        val validModality = when (inputMethod) {
            INPUT_SWIPE -> row.swipeVelocityX?.isFinite() == true
            INPUT_KEYBOARD -> row.swipeVelocityX == null
            else -> false
        }
        if (row.inputMethod != inputMethod || !validModality || row.inputRating != Rating.GOOD.value ||
            row.peekSemantics != PEEK_REQUIRED || row.peeked != true ||
            row.timingDiscardReason != null || row.latencyMs == null || row.presentationLength == null ||
            row.undoOf != null || row.rating == 0
        ) continue
        window.add(TimingSample(row.latencyMs, row.level, row.presentationLength))
    }
}

/** Never reclassify historical grades using today's calibration or preference. */
fun Scheduler.applyRecordedReview(card: CardEntity, review: ReviewEntity): ScheduleResult {
    require(review.gradingVersion == null || review.gradingVersion == DERIVED_GRADING_VERSION) {
        "Unsupported derived grading version: ${review.gradingVersion}"
    }
    val engine = review.fsrsParameters?.let {
        withParameters(dev.ikna.domain.fsrs.FsrsSnapshotCodec.decode(it))
    } ?: defaultSnapshot()
    val rating = Rating.of(review.rating)
    return if (review.gradingVersion == DERIVED_GRADING_VERSION) {
        require(review.inputRating == Rating.GOOD.value && rating in listOf(Rating.HARD, Rating.EASY)) {
            "Invalid derived grading record"
        }
        engine.applyDerivedV1(card, rating, review.ts)
    } else engine.apply(card, rating, review.ts)
}
