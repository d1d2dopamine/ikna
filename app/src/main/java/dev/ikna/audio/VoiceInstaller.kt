package dev.ikna.audio

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Installs a model, and keeps installing it after the screen has gone.
 *
 * The install used to be launched from the voice screen's own coroutine scope,
 * which is tied to the composition. For a Piper voice that is invisible: the
 * copying is over in two seconds and nobody leaves in the middle of it. For a
 * Kokoro release it was the whole difference between working and not -- the
 * unpacking takes minutes, a back press cancelled it, the half-written staging
 * folder was deleted, and nothing anywhere said either had happened. The user
 * came back to a screen with no new model on it and no reason given.
 *
 * So the work is owned here, held for the life of the process, and the screen
 * only watches. What this deliberately is not is a foreground service: Android
 * may still take the process while the app is in the background, and pretending
 * otherwise would be a promise the app cannot keep. It survives leaving the
 * screen; it does not survive being killed, and the screen says so.
 */
class VoiceInstaller(private val store: VoiceModelStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<VoiceInstallState>(VoiceInstallState.Idle)

    /** What is happening, for any screen that wants to show it. */
    val state: StateFlow<VoiceInstallState> = _state.asStateFlow()

    val running: Boolean get() = _state.value is VoiceInstallState.Running

    /** Copies in a picked folder. */
    fun installFolder(tree: Uri) = start { store.install(tree) { report(it) } }

    /** Unpacks a picked `.tar.bz2` and installs what comes out. */
    fun installArchive(source: Uri) = start { store.installArchive(source) { report(it) } }

    /**
     * Marks the last result as seen.
     *
     * Kept until then rather than dropped when the screen closes: an install
     * that finished while the user was elsewhere still has something to say, and
     * it should be there when they come back.
     */
    fun acknowledge() {
        if (_state.value is VoiceInstallState.Done) _state.value = VoiceInstallState.Idle
    }

    private fun start(work: suspend () -> VoiceModelResult) {
        // One at a time. Two unpackings share one staging folder and would eat
        // each other halfway through, and the second tap that starts one is
        // always an accident.
        if (running) return
        _state.value = VoiceInstallState.Running(VoiceInstallProgress())
        scope.launch {
            val result = runCatching { work() }
                .getOrElse { failure -> VoiceModelResult.Failed(failure.message) }
            _state.value = VoiceInstallState.Done(result)
        }
    }

    private fun report(progress: VoiceInstallProgress) {
        if (_state.value is VoiceInstallState.Running) {
            _state.value = VoiceInstallState.Running(progress)
        }
    }
}
