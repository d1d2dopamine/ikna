package dev.ikna.ui.session

import dev.ikna.domain.governor.GovernorReason
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFinishTest {
    private val zone = ZoneId.of("Asia/Yekaterinburg")
    private val noon = Instant.parse("2026-11-21T07:00:00Z")

    @Test
    fun `timing and review status select truthful messages`() {
        val state = SessionUiState(answeredToday = 4)
        assertTrue(sessionFinishMessageKey(state, noon, zone).startsWith("finish.done."))
        val imminent = state.copy(nextDueAt = noon.plusSeconds(900).toEpochMilli())
        assertTrue(sessionFinishMessageKey(imminent, noon, zone).startsWith("finish.soon."))
        assertTrue(sessionFinishMessageKey(state, noon.plusSeconds(11 * 3600), zone).startsWith("finish.night."))
        val introduced = state.copy(reason = GovernorReason.PACK_EXHAUSTED)
        assertTrue(sessionFinishMessageKey(introduced, noon, zone).startsWith("finish.introduced."))
    }

    @Test
    fun `variants hold steady during a visit and rotate over days`() {
        val state = SessionUiState(deckTitle = "English", answeredToday = 4)
        assertEquals(sessionFinishMessageKey(state, noon, zone), sessionFinishMessageKey(state, noon.plusSeconds(30), zone))
        val week = (0L..6L).map { sessionFinishMessageKey(state, noon.plusSeconds(it * 86_400), zone) }
        assertEquals(4, week.toSet().size)
    }
}
