package dev.ikna

import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.BrowsePolicy
import dev.ikna.ui.session.SWIPE_THRESHOLD
import dev.ikna.ui.session.browseAdvances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `the one-card minimum never unlocks browse`() {
        assertEquals(0, BrowsePolicy.quota(1, GovernorReason.OK))
        assertEquals(0, BrowsePolicy.quota(2, GovernorReason.OK))
        assertEquals(1, BrowsePolicy.quota(3, GovernorReason.OK))
    }

    @Test
    fun `the allowance is global and capped`() {
        assertEquals(5, BrowsePolicy.quota(17, GovernorReason.OK))
        assertEquals(6, BrowsePolicy.quota(18, GovernorReason.OK))
        assertEquals(6, BrowsePolicy.quota(90, GovernorReason.OK))
    }

    @Test
    fun `behavioural governor gates keep browse closed`() {
        val blocked = listOf(
            GovernorReason.RETURN_MODE,
            GovernorReason.OVERHEATED,
            GovernorReason.BACKLOG_LIMIT,
            GovernorReason.LOW_ACTIVITY,
            GovernorReason.LOW_ACCURACY,
            GovernorReason.POST_SKIP_WARMUP,
            GovernorReason.LATE_NIGHT
        )
        blocked.forEach { reason ->
            assertEquals(reason.name, 0, BrowsePolicy.quota(18, reason))
        }
    }

    @Test
    fun `safety valve retains the gate it overrode`() {
        assertEquals(
            0,
            BrowsePolicy.quota(18, GovernorReason.SAFETY_VALVE, GovernorReason.LOW_ACTIVITY)
        )
        assertEquals(
            6,
            BrowsePolicy.quota(18, GovernorReason.SAFETY_VALVE, GovernorReason.NO_HEADROOM)
        )
    }

    @Test
    fun `repeating one answer cannot complete three required questions`() {
        val required = BrowsePolicy.requiredIds(
            listOf("a:0", "a:0", "b:0", "c:0", "extra:0"),
            extraRequested = 1
        )
        assertEquals(listOf("a:0", "b:0", "c:0"), required)
        assertFalse(BrowsePolicy.requiredComplete(required, setOf("a:0")))
        assertTrue(BrowsePolicy.requiredComplete(required, setOf("a:0", "b:0", "c:0")))
    }

    @Test
    fun `only mature future cards are eligible`() {
        val now = 1_000L
        assertTrue(
            BrowsePolicy.eligible(
                activeReviewDays = 3,
                stability = 7.0,
                isNew = false,
                inAmnesty = false,
                dueAt = now + 1,
                now = now,
                reviewedToday = false,
                viewedRecently = false
            )
        )
        assertFalse(BrowsePolicy.eligible(2, 7.0, false, false, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, 6.9, false, false, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, 7.0, true, false, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, 7.0, false, true, now + 1, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, 7.0, false, false, now, now, false, false))
        assertFalse(BrowsePolicy.eligible(3, 7.0, false, false, now + 1, now, true, false))
        assertFalse(BrowsePolicy.eligible(3, 7.0, false, false, now + 1, now, false, true))
    }
}
