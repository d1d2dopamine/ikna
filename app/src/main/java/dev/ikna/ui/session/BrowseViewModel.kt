package dev.ikna.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.session.SessionCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BrowseUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val queue: List<SessionCard> = emptyList(),
    val deckTitle: String = ""
)

/** Android state holder for the passive vertical Browse feed. */
class BrowseViewModel(
    private val repo: LearningRepository,
    private val deckId: String
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private val work = Mutex()
    private val exposureAttempts = linkedSetOf<String>()

    private val loadGate = CardLoadGate()

    init { load() }

    fun load() {
        if (!loadGate.tryStart()) return
        _state.value = BrowseUiState(deckTitle = _state.value.deckTitle)
        viewModelScope.launch {
            try {
                work.withLock {
                    cardLoadResult { repo.startBrowse(deckId) }.fold(
                        onSuccess = { plan ->
                            _state.value = BrowseUiState(
                                loading = false, queue = plan.cards, deckTitle = plan.deckTitle
                            )
                        },
                        onFailure = {
                            _state.value = _state.value.copy(loading = false, loadFailed = true)
                        }
                    )
                }
            } finally { loadGate.finish() }
        }
    }

    /**
     * Record only cards that the lazy list reports as meaningfully visible.
     * Recomposition and lazy prefetch therefore cannot create Browse exposure.
     */
    fun recordVisible(indices: Iterable<Int>) {
        viewModelScope.launch {
            work.withLock {
                val queue = _state.value.queue
                for (index in indices.sorted()) {
                    val card = queue.getOrNull(index) ?: continue
                    if (!exposureAttempts.add(card.card.key)) continue
                    cardLoadResult { repo.recordBrowse(card, deckId) }
                }
            }
        }
    }

    companion object {
        fun factory(repo: LearningRepository, deckId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BrowseViewModel(repo, deckId) as T
            }
    }
}
