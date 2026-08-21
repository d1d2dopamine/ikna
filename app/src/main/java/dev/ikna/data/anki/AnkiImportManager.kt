package dev.ikna.data.anki

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnkiImportState {
    data object Idle : AnkiImportState
    data object Running : AnkiImportState
    data class Done(val result: AnkiImportResult) : AnkiImportState
    data class Failed(val error: AnkiImportError) : AnkiImportState
}

/**
 * App-lifetime owner for a package import.
 *
 * The screen may disappear while SQLite is replaying years of history. Keeping
 * the job here means navigation does not cancel it; killing the process still
 * cannot leave half an import because AnkiImporter owns one Room transaction.
 */
class AnkiImportManager(private val importer: AnkiImporter) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AnkiImportState>(AnkiImportState.Idle)
    val state: StateFlow<AnkiImportState> = _state.asStateFlow()

    @Synchronized
    fun start(uri: Uri, appLanguage: String) {
        if (_state.value is AnkiImportState.Running) return
        _state.value = AnkiImportState.Running
        scope.launch {
            _state.value = try {
                AnkiImportState.Done(importer.importPackage(uri, appLanguage))
            } catch (known: AnkiImportException) {
                AnkiImportState.Failed(known.error)
            } catch (_: Throwable) {
                AnkiImportState.Failed(AnkiImportError.FAILED)
            }
        }
    }

    @Synchronized
    fun reset() {
        if (_state.value !is AnkiImportState.Running) _state.value = AnkiImportState.Idle
    }
}
