package dev.ikna.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Where one day ends and the next one begins.
 *
 * Every counter in this app is keyed by a day string, and the obvious
 * boundary — midnight — is the wrong one here. Delayed sleep phase is close to
 * standard equipment with ADHD: the session that happens at 01:00 is the
 * evening session, not the morning one. With a midnight boundary it counted
 * towards a day that had not started yet, so the activity map grew a hole for
 * a day that was worked, the measured norm dropped, and the load governor
 * reacted to a break that never happened.
 *
 * All of that logic is here, in one small class with no Android dependency,
 * because it is exactly the kind of arithmetic that is easy to get wrong and
 * cheap to unit test.
 */
class DayBoundary(
    private val startHour: Int,
    private val zone: ZoneId = ZoneId.systemDefault()
) {

    /** The calendar day a moment belongs to. */
    fun date(ts: Long): LocalDate =
        Instant.ofEpochMilli(ts).atZone(zone).minusHours(startHour.toLong()).toLocalDate()

    /** That day as the `yyyy-MM-dd` key used by every stats table. */
    fun key(ts: Long): String = FORMAT.format(date(ts))

    /** The moment that day began, in epoch millis. */
    fun startOfDay(ts: Long): Long =
        date(ts).atStartOfDay(zone).plusHours(startHour.toLong()).toInstant().toEpochMilli()

    /**
     * The stretch where no new material is handed out: from the cutoff in the
     * evening until the day rolls over. Reviews are never blocked.
     */
    fun isNight(ts: Long, cutoffHour: Int): Boolean {
        val hour = Instant.ofEpochMilli(ts).atZone(zone).hour
        return hour >= cutoffHour || hour < startHour
    }

    private companion object {
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
