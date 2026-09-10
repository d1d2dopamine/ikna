package dev.ikna

import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.BrowseCreditLedger
import dev.ikna.domain.session.BrowsePolicy
import dev.ikna.domain.session.BrowseUnavailableReason
import dev.ikna.ui.session.SWIPE_THRESHOLD
import dev.ikna.ui.session.browseAdvances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowsePolicyTest {
    @Test
    fun `browse only advances to the right`() {
        assertFalse(browseAdvances(-SWIPE_THRESHOLD * 2f))
        assertFalse(browseAdvances(-40f, velocityX = -5_000f))
        assertFalse(browseAdvances(SWIPE_THRESHOLD - 1f))
        assertTrue(browseAdvances(SWIPE_THRESHOLD))
        assertTrue(browseAdvances(SWIPE_THRESHOLD / 2f, velocityX = 900f))
    }

    @Test
    fun `three completed one-card days earn exactly one browse card`() {
        var ledger = BrowseCreditLedger()
        listOf("2026-09-10", "2026-09-11", "2026-09-12").forEachIndexed { index, day ->
            val settled = BrowsePolicy.settleCredits(ledger, day, 1, totalExposures = 0)
            ledger = settled.ledger
            assertEquals(index + 1, settled.availablePoints)
        }
        assertEquals(1, BrowsePolicy.cardsForCredits(ledger.earnedPoints))

        val duplicate = BrowsePolicy.settleCredits(ledger, "2026-09-12", 90, 0)
        assertEquals(3, duplicate.availablePoints)
        val clockBack = BrowsePolicy.settleCredits(duplicate.ledger, "2026-09-11", 90, 0)
        assertEquals(3, clockBack.availablePoints)
    }

    @Test
    fun `an unfinished plan earns nothing`() {
        val pending = BrowsePolicy.settleCredits(
            BrowseCreditLedger(), "2026-09-10", completedRequiredCards = null,
            totalExposures = 0
        )
        assertEquals(0, pending.availablePoints)
        assertNull(pending.ledger.creditedDay)

        val complete = BrowsePolicy.settleCredits(pending.ledger, "2026-09-10", 3, 0)
        assertEquals(3, complete.availablePoints)
        assertEquals("2026-09-10", complete.ledger.creditedDay)
    }

    @Test
    fun `the global bank is capped and every visible card spends three points`() {
        val full = BrowsePolicy.settleCredits(BrowseCreditLedger(), "2026-09-10", 90, 0)
        assertEquals(BrowsePolicy.MAX_BANK_POINTS, full.availablePoints)
        assertEquals(6, BrowsePolicy.cardsForCredits(full.availablePoints))

        val oneRead = BrowsePolicy.settleCredits(full.ledger, "2026-09-10", 90, 1)
        assertEquals(15, oneRead.availablePoints)
        assertEquals(5, BrowsePolicy.cardsForCredits(oneRead.availablePoints))

        val sixReads = BrowsePolicy.settleCredits(oneRead.ledger, "2026-09-10", 90, 6)
        assertEquals(0, sixReads.availablePoints)
    }

    @Test
    fun `old exposures form a baseline instead of historical debt`() {
        val upgraded = BrowsePolicy.settleCredits(
            BrowseCreditLedger(), "2026-09-10", completedRequiredCards = null,
            totalExposures = 27
        )
        assertEquals(27, upgraded.ledger.exposureBaseline)
        assertEquals(0, upgraded.availablePoints)

        val completed = BrowsePolicy.settleCredits(upgraded.ledger, "2026-09-10", 3, 27)
        assertEquals(3, completed.availablePoints)
    }

    @Test
    fun `a database wipe also resets a surviving preference ledger`() {
        val stale = BrowseCreditLedger(
            earnedPoints = 30,
            exposureBaseline = 0,
            lastExposureCount = 10,
            creditedDay = "2026-09-09"
        )
        val reset = BrowsePolicy.settleCredits(stale, "2026-09-10", 1, totalExposures = 0)
        assertEquals(0, reset.ledger.exposureBaseline)
        assertEquals(1, reset.availablePoints)
    }

    @Test
    fun `first run is not disguised as a load failure`() {
        assertNull(BrowsePolicy.safetyBlock(GovernorReason.FIRST_RUN))
        assertNull(BrowsePolicy.safetyBlock(GovernorReason.OK))
        assertNull(BrowsePolicy.safetyBlock(GovernorReason.NO_HEADROOM))
        assertNull(BrowsePolicy.safetyBlock(GovernorReason.PACK_EXHAUSTED))
    }

    @Test
    fun `every real governor gate keeps its own explanation`() {
        val expected = mapOf(
            GovernorReason.BACKLOG_LIMIT to BrowseUnavailableReason.BACKLOG_GUARD,
            GovernorReason.POST_SKIP_WARMUP to BrowseUnavailableReason.POST_SKIP_GUARD,
            GovernorReason.LOW_ACTIVITY to BrowseUnavailableReason.LOW_ACTIVITY_GUARD,
            GovernorReason.LATE_NIGHT to BrowseUnavailableReason.LATE_NIGHT,
            GovernorReason.LOW_ACCURACY to BrowseUnavailableReason.LOW_ACCURACY_GUARD,
            GovernorReason.RETURN_MODE to BrowseUnavailableReason.RETURN_GUARD,
            GovernorReason.OVERHEATED to BrowseUnavailableReason.OVERHEATED_GUARD
        )
        expected.forEach { (reason, blocker) ->
            assertEquals(reason.name, blocker, BrowsePolicy.safetyBlock(reason))
        }
    }

    @Test
    fun `safety valve retains the gate it overrode`() {
        assertEquals(
            BrowseUnavailableReason.LOW_ACTIVITY_GUARD,
            BrowsePolicy.safetyBlock(
                GovernorReason.SAFETY_VALVE,
                GovernorReason.LOW_ACTIVITY
            )
        )
        assertNull(
            BrowsePolicy.safetyBlock(
                GovernorReason.SAFETY_VALVE,
                GovernorReason.NO_HEADROOM
            )
        )
    }

    @Test
    fun `repeating one answer cannot complete three required questions`() {
        val required = BrowsePolicy.requiredIds(
            listOf("a:0", "a:0", "b:0", "c:0", "extra:0"),
            extraRequested = 1
        )
        assertEquals(listOf("a:0", "b:0", "c:0"), required)
        assertEquals(2, BrowsePolicy.remainingRequired(required, setOf("a:0")))
        assertFalse(BrowsePolicy.requiredComplete(required, setOf("a:0")))
        assertTrue(BrowsePolicy.requiredComplete(required, setOf("a:0", "b:0", "c:0")))
    }

    @Test
    fun `only familiar future cards are eligible`() {
        val now = 1_000L
        assertTrue(
            BrowsePolicy.eligible(
                successfulReviewDays = 3,
                isNew = false,
                inAmnesty = false,
                dueAt = now + 1,
                now = now,
                reviewedToday = false,
                viewedRecently = false
            )
        )
        assertFalse(BrowsePolicy.eligible(2, false, false, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, true, false, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, false, true, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, false, false, now, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, false, false, now + 1, now, true, false))
        assertFalse(BrowsePolicy.eligible(3, false, false, now + 1, now, false, true))
    }
}
