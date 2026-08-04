package dev.ikna.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.domain.session.SessionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionUiState(
    val loading: Boolean = true,
    val queue: List<SessionCard> = emptyList(),
    val index: Int = 0,
    val revealed: Boolean = false,
    val visibleCount: Int = 0,
    val answeredThisSession: Int = 0,
    val dailyMinimum: Int = 1,
    val reason: GovernorReason = GovernorReason.OK,
    val finished: Boolean = false
) {
    val current: SessionCard? get() = queue.getOrNull(index)
    /** Clamped by SessionBuilder. This number can never become intimidating. */
    val remaining: Int get() = (visibleCount - index).coerceAtLeast(0)
    val minimumMet: Boolean get() = answeredThisSession >= dailyMinimum
}

class SessionViewModel(
    private val repo: LearningRepository,
    private val dailyMinimum: Int
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState(dailyMinimum = dailyMinimum))
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var shownAt: Long = System.currentTimeMillis()

    init { load() }

    fun load() {
        viewModelScope.launch {
            repo.runDailyPlan()
            val plan = repo.buildSession()
            _state.value = SessionUiState(
                loading = false,
                queue = plan.cards,
                visibleCount = plan.visibleCount,
                dailyMinimum = dailyMinimum,
                reason = plan.decision.reason,
                finished = plan.cards.isEmpty()
            )
            shownAt = System.currentTimeMillis()
        }
    }

    fun reveal() {
        _state.value = _state.value.copy(revealed = true)
    }

    fun answer(rating: Rating) {
        val s = _state.value
        val card = s.current ?: return
        val now = System.currentTimeMillis()
        val duration = (now - shownAt).coerceAtMost(120_000L)

        viewModelScope.launch { repo.answer(card, rating, duration, now) }

        val nextIndex = s.index + 1
        _state.value = s.copy(
            index = nextIndex,
            revealed = false,
            answeredThisSession = s.answeredThisSession + 1,
            finished = nextIndex >= s.queue.size
        )
        shownAt = now
    }
}
