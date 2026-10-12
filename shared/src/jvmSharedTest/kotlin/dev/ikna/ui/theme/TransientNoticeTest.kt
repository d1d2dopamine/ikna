package dev.ikna.ui.theme

import androidx.compose.ui.platform.AccessibilityManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransientNoticeTest {
    @Test fun ordinaryDurationAndInvalidInputAreBounded() {
        assertEquals(5_000L, noticeTimeoutMillis(5_000L, null))
        assertEquals(1L, noticeTimeoutMillis(-1L, null))
    }

    @Test fun recommendedReadingTimeIncludesDismissControl() {
        val manager = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long, containsIcons: Boolean,
                containsText: Boolean, containsControls: Boolean
            ): Long {
                assertEquals(5_000L, originalTimeoutMillis)
                assertFalse(containsIcons)
                assertTrue(containsText)
                assertTrue(containsControls)
                return 60_000L
            }
        }
        assertEquals(60_000L, noticeTimeoutMillis(5_000L, manager))
    }

    @Test fun accessibilityCanKeepNoticeWithoutOverflowButCannotShortenIt() {
        for (recommended in listOf(0L, 2_000L, Long.MAX_VALUE)) {
            val manager = object : AccessibilityManager {
                override fun calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis: Long, containsIcons: Boolean,
                    containsText: Boolean, containsControls: Boolean
                ) = recommended
            }
            assertEquals(recommended.coerceAtLeast(5_000L), noticeTimeoutMillis(5_000L, manager))
        }
    }
}
