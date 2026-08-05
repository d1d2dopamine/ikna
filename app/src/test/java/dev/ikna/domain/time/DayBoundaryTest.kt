package dev.ikna.domain.time

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayBoundaryTest {

    private val zone = ZoneId.of("Europe/Warsaw")
    private val boundary = DayBoundary(startHour = 4, zone = zone)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `a session after midnight still counts as the evening before`() {
        assertEquals("2026-03-10", boundary.key(at(2026, 3, 11, 1, 30)))
    }

    @Test
    fun `the day rolls over at four in the morning`() {
        assertEquals("2026-03-10", boundary.key(at(2026, 3, 11, 3, 59)))
        assertEquals("2026-03-11", boundary.key(at(2026, 3, 11, 4, 0)))
    }

    @Test
    fun `evening and the night after it share one day key`() {
        assertEquals(
            boundary.key(at(2026, 3, 10, 22, 0)),
            boundary.key(at(2026, 3, 11, 2, 0))
        )
    }

    @Test
    fun `start of day is the boundary hour, not midnight`() {
        assertEquals(at(2026, 3, 10, 4, 0), boundary.startOfDay(at(2026, 3, 11, 1, 30)))
        assertEquals(at(2026, 3, 11, 4, 0), boundary.startOfDay(at(2026, 3, 11, 9, 0)))
    }

    @Test
    fun `new material is blocked from the cutoff until the day rolls over`() {
        assertTrue(boundary.isNight(at(2026, 3, 10, 23, 30), cutoffHour = 23))
        assertTrue(boundary.isNight(at(2026, 3, 11, 2, 0), cutoffHour = 23))
        assertFalse(boundary.isNight(at(2026, 3, 11, 9, 0), cutoffHour = 23))
        assertFalse(boundary.isNight(at(2026, 3, 11, 22, 0), cutoffHour = 23))
    }

    @Test
    fun `a midnight boundary would have split that same evening in two`() {
        val midnight = DayBoundary(startHour = 0, zone = zone)
        assertNotEquals(
            midnight.key(at(2026, 3, 10, 22, 0)),
            midnight.key(at(2026, 3, 11, 2, 0))
        )
    }
}
