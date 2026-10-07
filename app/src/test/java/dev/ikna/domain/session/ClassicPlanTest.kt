package dev.ikna.domain.session

import dev.ikna.data.db.DailyPlanEntity
import org.junit.Assert.*
import org.junit.Test

class ClassicPlanTest {
    private fun plan(ids: String, extra: Int) = DailyPlanEntity("2026-10-08", ids,
        ids.split(",").size, 40, 5, 3, "OK", extra, 123L)
    @Test fun `retired required and extra keys leave without increasing allowances`() {
        val original = plan("word:0,word:1,second:0,second:2,extra:0,extra:1", 2)
        val active = original.classicOnly()
        assertEquals(listOf("word:0", "second:0", "extra:0"), active.ids)
        assertEquals(3, active.plannedTotal)
        assertEquals(1, active.extraRequested)
        assertEquals(original.allowedNew, active.allowedNew)
        assertEquals(original.capacity, active.capacity)
        assertEquals(original.createdAt, active.createdAt)
        assertEquals(active, active.classicOnly())
    }
    @Test fun `existing classic plan is returned intact`() {
        val original = plan("target:with:colon:0,extra:0", 1)
        assertSame(original, original.classicOnly())
    }
}
