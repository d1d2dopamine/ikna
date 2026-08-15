package dev.ikna.domain.governor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The streak decides whether the day's ceiling is allowed to rise, so both of
 * the bugs it used to have are worth a test each: an absence that was stepped
 * over, and a morning that reset everything.
 */
class CleanStreakTest {

    private val days = listOf("2026-08-16", "2026-08-15", "2026-08-14", "2026-08-13", "2026-08-12")

    @Test
    fun `an unfinished today does not end a streak that is still running`() {
        val streak = CleanStreak.count(
            days,
            mapOf(
                "2026-08-16" to false,
                "2026-08-15" to true,
                "2026-08-14" to true
            )
        )
        assertEquals("Today is in progress; the two days before it were clean.", 2, streak)
    }

    @Test
    fun `today counts as soon as its plan is finished`() {
        val streak = CleanStreak.count(
            days,
            mapOf(
                "2026-08-16" to true,
                "2026-08-15" to true,
                "2026-08-14" to true
            )
        )
        assertEquals(3, streak)
    }

    @Test
    fun `a day with no session at all breaks the streak`() {
        val streak = CleanStreak.count(
            days,
            // 2026-08-14 has no row: nothing happened that day. The old count walked
            // rows rather than days and joined the two sides of the gap together.
            mapOf(
                "2026-08-16" to true,
                "2026-08-15" to true,
                "2026-08-13" to true,
                "2026-08-12" to true
            )
        )
        assertEquals("The gap on the 14th ends it at two.", 2, streak)
    }

    @Test
    fun `an unfinished day breaks the streak`() {
        val streak = CleanStreak.count(
            days,
            mapOf(
                "2026-08-16" to true,
                "2026-08-15" to false,
                "2026-08-14" to true
            )
        )
        assertEquals(1, streak)
    }

    @Test
    fun `an empty history is not a streak`() {
        assertEquals(0, CleanStreak.count(days, emptyMap()))
        assertEquals(0, CleanStreak.count(emptyList(), emptyMap()))
    }

    @Test
    fun `a first day that is already finished counts once`() {
        assertEquals(1, CleanStreak.count(days, mapOf("2026-08-16" to true)))
    }
}
