package dev.ikna.domain.session

import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewDurationTest {
    @Test fun `new answer duration is bounded at both extremes`() {
        assertEquals(0L, ReviewDuration.normalize(Long.MIN_VALUE))
        assertEquals(0L, ReviewDuration.normalize(-1L))
        assertEquals(0L, ReviewDuration.normalize(0L))
        assertEquals(37L, ReviewDuration.normalize(37L))
        assertEquals(120_000L, ReviewDuration.normalize(120_000L))
        assertEquals(120_000L, ReviewDuration.normalize(Long.MAX_VALUE))
    }

    @Test fun `elapsed nanos convert to milliseconds then clamp`() {
        assertEquals(0L, ReviewDuration.elapsedMillis(1_000L, 1_000L))
        assertEquals(0L, ReviewDuration.elapsedMillis(1_000_000L, 0L))
        assertEquals(0L, ReviewDuration.elapsedMillis(0L, 999_999L))
        assertEquals(1L, ReviewDuration.elapsedMillis(0L, 1_000_000L))
        assertEquals(119_999L, ReviewDuration.elapsedMillis(0L, 119_999_999_999L))
        assertEquals(120_000L, ReviewDuration.elapsedMillis(0L, 120_000_000_000L))
        assertEquals(120_000L, ReviewDuration.elapsedMillis(0L, Long.MAX_VALUE))
    }

    @Test fun `nanoTime signed origin and wrap do not change short elapsed intervals`() {
        assertEquals(5_000L, ReviewDuration.elapsedMillis(-10_000_000_000L, -5_000_000_000L))
        assertEquals(1L, ReviewDuration.elapsedMillis(Long.MAX_VALUE - 500_000L, Long.MIN_VALUE + 499_999L))
        assertEquals(120_000L, ReviewDuration.elapsedMillis(Long.MAX_VALUE - 100_000_000_000L,
            Long.MIN_VALUE + 100_000_000_000L))
    }
    @Test fun `both platform routes measure duration monotonically and retain wall event timestamps`() {
        val android = source("app/src/main/java/dev/ikna/ui/session/SessionViewModel.kt")
        val desktop = source("desktop/src/main/kotlin/dev/ikna/desktop/SessionPane.kt")
        for (ui in listOf(android, desktop)) {
            assertTrue(ui.contains("ReviewDuration.elapsedMillis(shownNanos, System.nanoTime())"))
            assertTrue(ui.contains("shownNanos = System.nanoTime()"))
            assertFalse(ui.contains("shownNanos = System.currentTimeMillis()"))
            assertFalse(ui.contains("System.currentTimeMillis() - shown"))
            assertFalse(ui.contains("now - shown"))
        }
        assertTrue(android.contains("val now = System.currentTimeMillis()"))
        assertTrue(android.contains("repo.answer(card, rating, duration, now, signals)"))
        assertTrue(desktop.contains("now = System.currentTimeMillis(), signals = signals"))
    }

    private fun source(relative: String): String = listOf(File(relative), File("../$relative"))
        .firstOrNull { it.isFile }?.readText() ?: error("Source not found: $relative")

}
