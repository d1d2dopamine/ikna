package dev.ikna.ui.stats

import dev.ikna.data.repo.HourSlice
import dev.ikna.ui.text.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsDescriptionsTest {
    private val english: (String) -> String = { STRINGS_EN.getValue(it) }

    @Test
    fun activitySummarizesThePeriodWithoutReadingEveryCell() {
        assertEquals(
            "Days with a session: 2 of 3.",
            activityDescription(listOf(true, false, true), english)
        )
        assertEquals("Days with a session: 0 of 2.", activityDescription(listOf(false, false), english))
        assertEquals("No data yet.", activityDescription(emptyList(), english))
    }

    @Test
    fun hoursSummarizeOnlyTheRepositoryVerdictAndKeepZeroDistinctFromMissing() {
        val hours = listOf(HourSlice(18, 12, 0.0), HourSlice(7, 11, 1.0))
        assertEquals(
            "Strongest observed hour: 18:00: 0% recalled; reviews: 12.",
            hoursDescription(hours, 18, english)
        )
        assertEquals("Small sample.", hoursDescription(hours, null, english))
        assertEquals("Small sample.", hoursDescription(hours, 7, english))
        assertEquals("No data yet.", hoursDescription(emptyList(), null, english))
        assertFalse(hoursDescription(hours, 18, english).contains("07:00"))
    }

    @Test
    fun forecastStartsAtOneAndKeepsZeros() {
        assertEquals(
            "Next 3 days, starting tomorrow. Scheduled reviews: 3, 0, 8.",
            forecastDescription(listOf(3, 0, 8), english)
        )
        assertEquals("No data yet.", forecastDescription(emptyList(), english))
    }

    @Test
    fun allSevenLocalesResolveEveryChartPlaceholder() {
        for (table in listOf(STRINGS_RU, STRINGS_EN, STRINGS_PL, STRINGS_ES, STRINGS_FR, STRINGS_DE, STRINGS_PT)) {
            val text: (String) -> String = { table.getValue(it) }
            val descriptions = listOf(
                activityDescription(listOf(true, false), text),
                activityDescription(listOf(false), text),
                hoursDescription(listOf(HourSlice(3, 12, 0.5)), 3, text),
                hoursDescription(listOf(HourSlice(3, 4, 0.5)), null, text),
                forecastDescription(listOf(4, 0), text),
                forecastDescription(emptyList(), text)
            )
            assertTrue(descriptions.all { it.isNotBlank() && '{' !in it && '}' !in it })
        }
    }
}
