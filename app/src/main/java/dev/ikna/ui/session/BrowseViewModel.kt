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
    val queue: List<SessionCard> = emptyList(),
    val index: Int = 0,
    val deckTitle: String = "",
    val advancing: Boolean = false,
    val finished: Boolean = false
) {
    val current: SessionCard?
        get() = if (finished) null else queue.getOrNull(index)

    val hadCards: Boolean
        get() = queue.isNotEmpty()
}

/** Android state holder for the passive, always-revealed card queue. */
class BrowseViewModel(
    private val repo: LearningRepository,
    private val deckId: String
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private val work = Mutex()

    init {
        viewModelScope.launch {
            work.withLock {
                val plan = repo.startBrowse(deckId)
                _state.value = BrowseUiState(
                    loading = false,
                    queue = plan.cards,
                    deckTitle = plan.deckTitle,
                    finished = plan.cards.isEmpty()
                )
            }
        }
    }

    fun next() {
        if (_state.value.loading || _state.value.advancing || _state.value.finished) return
        _state.value = _state.value.copy(advancing = true)
        viewModelScope.launch {
            work.withLock {
                val current = _state.value
                val nextIndex = current.index + 1
                val next = current.queue.getOrNull(nextIndex)
                if (next == null) {
                    _state.value = current.copy(advancing = false, finished = true)
                    return@withLock
                }

                val recorded = repo.recordBrowse(next, deckId)
                _state.value = if (recorded) {
                    current.copy(index = nextIndex, advancing = false)
                } else {
                    current.copy(advancing = false, finished = true)
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
