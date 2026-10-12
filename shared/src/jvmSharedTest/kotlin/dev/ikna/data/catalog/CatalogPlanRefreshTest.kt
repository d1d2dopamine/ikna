package dev.ikna.data.catalog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogPlanRefreshTest {
    @Test fun successAndRetryOnlyRefreshThePlan() = runBlocking {
        var calls = 0
        assertFalse(refreshCatalogStudyPlan { calls++; error("injected plan failure") })
        assertTrue(refreshCatalogStudyPlan { calls++ })
        assertEquals(2, calls)
    }
    @Test fun cancellationRemainsCancellation() = runBlocking {
        assertFailsWith<CancellationException> {
            refreshCatalogStudyPlan { throw CancellationException("screen closed") }
        }
        Unit
    }
}
