package dev.ikna.domain.governor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The governor is the whole point of the app, and it is pure arithmetic, so it
 * is also the cheapest thing here to protect. Each test pins one promise the
 * product makes to the user.
 */
class LoadGovernorTest {

    private val config = GovernorConfig()
    private val governor = LoadGovernor(config)

    private fun signals(
        dueToday: Int = 0,
        forecastAvg3d: Double = 0.0,
        backlog: Int = 0,
        accuracyRecent: Double = 1.0,
        activityRatio: Double = 1.0,
        daysSinceLastSession: Int = 0,
        daysSinceStart: Int = 10,
        reviewsDoneToday: Int = 0,
        cleanDays: Int = 0,
        newIntroducedLastWeek: Int = 3,
        totalReviews: Int = 500
    ) = GovernorSignals(
        dueToday = dueToday,
        forecastAvg3d = forecastAvg3d,
        backlog = backlog,
        accuracyRecent = accuracyRecent,
        activityRatio = activityRatio,
        daysSinceLastSession = daysSinceLastSession,
        daysSinceStart = daysSinceStart,
        reviewsDoneToday = reviewsDoneToday,
        cleanDays = cleanDays,
        newIntroducedLastWeek = newIntroducedLastWeek,
        totalReviews = totalReviews
    )

    @Test
    fun `a fresh install gets a first batch`() {
        val decision = governor.decide(signals(totalReviews = 0))
        assertEquals(GovernorReason.FIRST_RUN, decision.reason)
        assertEquals(config.maxNewPerDay, decision.allowedNew)
    }

    @Test
    fun `an empty queue introduces up to the daily ceiling and no further`() {
        val decision = governor.decide(signals())
        assertEquals(GovernorReason.OK, decision.reason)
        assertEquals(config.maxNewPerDay, decision.allowedNew)
    }

    @Test
    fun `new material costs review capacity`() {
        // Capacity is fully spoken for by cards already due today.
        val decision = governor.decide(signals(dueToday = config.targetDailyReviews))
        assertEquals(0, decision.allowedNew)
        assertTrue(decision.headroom <= 0.0)
    }

    @Test
    fun `a pile stops new material`() {
        val decision = governor.decide(signals(backlog = config.backlogHardLimit + 1))
        assertEquals(GovernorReason.BACKLOG_LIMIT, decision.reason)
        assertEquals(0, decision.allowedNew)
    }

    @Test
    fun `a quiet week stops new material`() {
        val decision = governor.decide(signals(activityRatio = 0.1))
        assertEquals(GovernorReason.LOW_ACTIVITY, decision.reason)
        assertEquals(0, decision.allowedNew)
    }

    @Test
    fun `coming back after a long break puts the app in return mode`() {
        val decision = governor.decide(signals(daysSinceLastSession = config.returnModeGapDays))
        assertEquals(GovernorReason.RETURN_MODE, decision.reason)
        assertEquals(config.returnModeCapacity, decision.capacity)
        assertEquals(0, decision.allowedNew)
    }

    @Test
    fun `the safety valve keeps one new chunk a week no matter what`() {
        val decision = governor.decide(
            signals(
                backlog = config.backlogHardLimit + 1,
                activityRatio = 0.0,
                accuracyRecent = 0.0,
                newIntroducedLastWeek = 0
            )
        )
        assertEquals(GovernorReason.SAFETY_VALVE, decision.reason)
        assertEquals(1, decision.allowedNew)
    }

    @Test
    fun `the ceiling stays flat while the habit is still settling`() {
        val settling = governor.decide(
            signals(daysSinceStart = config.settlingDays - 1, cleanDays = 30)
        )
        assertEquals(config.maxNewPerDay, settling.allowedNew)
    }

    @Test
    fun `part of every session is reserved for the amnesty pool`() {
        val decision = governor.decide(signals())
        assertTrue(decision.amnestyQuota > 0)
        assertTrue(decision.amnestyQuota < decision.capacity)
    }
}
