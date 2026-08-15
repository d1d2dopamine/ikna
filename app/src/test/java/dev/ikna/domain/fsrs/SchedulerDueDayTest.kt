package dev.ikna.domain.fsrs

import dev.ikna.data.db.CardEntity
import dev.ikna.domain.time.DayBoundary
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An interval is a number of days, and this app keys everything by a study day
 * that starts at 04:00. So "comes back in three days" has to mean "is waiting on
 * the third day", not "appears at 23:47 on the third day".
 *
 * Nothing here asserts a particular interval length -- that is FSRS's business
 * and FsrsTest's. These tests only pin down where in a day a due time lands, and
 * they compare the two schedulers against each other so they keep saying the
 * same thing if the weights are ever retuned. Times are built from local wall
 * clock on purpose: the boundary follows the phone's zone and so does the test.
 */
class SchedulerDueDayTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val boundary = DayBoundary(DAY_START)

    /** Snapping scheduler, as the app builds it. */
    private val byDay = Scheduler(FsrsParams(), DAY_START)

    /** Clock-time scheduling: what the app did before. */
    private val byClock = Scheduler(FsrsParams())

    private fun at(day: Int, hour: Int): Long =
        LocalDateTime.of(2026, 3, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** An evening session on the study day the given due time belongs to. */
    private fun eveningOfSameDay(dueAt: Long): Long =
        boundary.startOfDay(dueAt) + 16 * 60 * 60 * 1000L

    private fun card(now: Long) = CardEntity(
        chunkId = "c1",
        level = 0,
        stability = 5.0,
        difficulty = 5.0,
        dueAt = now,
        lastReviewAt = now - 5 * DAY_MS,
        introducedAt = now - 30 * DAY_MS,
        reps = 4,
        isNew = false
    )

    @Test
    fun `a card answered late at night is waiting from the start of its day`() {
        val now = at(10, 23)
        val snapped = byDay.apply(card(now), Rating.AGAIN, now).card.dueAt
        val clock = byClock.apply(card(now), Rating.AGAIN, now).card.dueAt

        assertEquals(
            "The due time is the start of the study day the interval lands in.",
            boundary.startOfDay(clock),
            snapped
        )
        assertEquals(
            "Snapping does not change which day FSRS chose.",
            boundary.key(clock),
            boundary.key(snapped)
        )
        assertTrue("A due time is never moved into the past.", snapped > now)

        // The point of all of it: an evening session on the day the card is due
        // meets the card, instead of the card arriving after that session ended.
        assertTrue(
            "A card answered at 23:00 has to be available during the evening " +
                "session of its due day.",
            snapped <= eveningOfSameDay(clock)
        )
        assertTrue(
            "Clock-time scheduling misses that session, so every interval was " +
                "quietly stretched by up to a day, every review.",
            clock > eveningOfSameDay(clock)
        )
    }

    @Test
    fun `a card answered in the afternoon is not moved later`() {
        val now = at(10, 15)
        val snapped = byDay.apply(card(now), Rating.GOOD, now).card.dueAt
        val clock = byClock.apply(card(now), Rating.GOOD, now).card.dueAt

        assertTrue(
            "Snapping can only bring a card forward, so nothing is ever hidden " +
                "for longer than the scheduler intended.",
            snapped <= clock
        )
        assertEquals(boundary.key(clock), boundary.key(snapped))
        assertTrue(snapped > now)
        assertEquals(boundary.startOfDay(snapped), snapped)
    }

    @Test
    fun `a first contact is still due immediately`() {
        // A chunk whose words the learner does not know yet is written due now,
        // so it reaches today's plan. Snapping must not push that into tomorrow.
        val now = at(10, 15)
        val prior = ComponentPrior(
            knownRatio = 0.0,
            unknownContentTokens = 2,
            weakLemmas = emptyList()
        )
        val fresh = byDay.introduce("c9", level = 0, componentPrior = prior, now = now)
        assertEquals(now, fresh.dueAt)
    }

    @Test
    fun `a chunk whose words are known starts on a day boundary`() {
        val now = at(10, 15)
        val prior = ComponentPrior(
            knownRatio = 1.0,
            unknownContentTokens = 0,
            weakLemmas = emptyList()
        )
        val fresh = byDay.introduce("c9", level = 0, componentPrior = prior, now = now)
        assertTrue(fresh.dueAt > now)
        assertEquals(boundary.startOfDay(fresh.dueAt), fresh.dueAt)
    }

    private companion object {
        /** The app's own default, from GovernorConfig.dayStartHour. */
        const val DAY_START = 4
    }
}
