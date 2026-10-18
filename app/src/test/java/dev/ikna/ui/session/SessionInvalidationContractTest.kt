package dev.ikna.ui.session

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Source wiring evidence only; not an Android lifecycle runtime test. */
class SessionInvalidationContractTest {
    @Test fun `committed sync changes notify open Android sessions`() {
        val container = source("app/src/main/java/dev/ikna/AppContainer.kt")
        assertFalse("Android currently discards committed merge notifications", container.contains("onMerged = {}"))
        assertTrue(container.contains("sessionChanges"))
    }

    @Test fun `Android resume checks saved history without automatic session admission`() {
        val screen = source("app/src/main/java/dev/ikna/ui/session/SessionScreen.kt")
        assertTrue("Session currently has no real resume observation", screen.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(screen.contains("vm.checkSessionOnResume()"))
    }

    @Test fun `automatic refresh never builds a daily plan or replays accepted answer input`() {
        val vm = source("app/src/main/java/dev/ikna/ui/session/SessionViewModel.kt")
        val refresh = vm.substringAfter("private suspend fun refreshFromHistory()").substringBefore("init {")
        assertTrue(refresh.contains("repo.inspectSession(deckId)"))
        assertFalse(refresh.contains("buildSession("))
        assertFalse(refresh.contains("repo.answer("))
        assertTrue(refresh.contains("changedState(refreshVersion)"))
        assertTrue(refresh.contains("failureVersion = refreshVersion"))
        assertTrue(refresh.contains("if (failureVersion == presentationVersion)"))
        assertTrue(vm.contains("Channel<Unit>(Channel.CONFLATED)"))
        assertTrue(vm.contains("if (!rememberOwnAction(version)) return false"))
        assertTrue(vm.contains("SessionObservationPolicy.compatible(baseline, fresh.observation, actionChange)"))
        assertTrue(vm.contains("if (!publishActionState(version,"))
    }

    @Test fun `changed session has explicit continue distinct from retry and completion`() {
        val screen = source("app/src/main/java/dev/ikna/ui/session/SessionScreen.kt")
        assertTrue(screen.contains("state.sessionChanged -> IknaSessionChanged(onContinue = { vm.load() })"))
        assertTrue(screen.contains("onDispose { owner?.lifecycle?.removeObserver(observer) }"))
        val state = source("shared/src/jvmShared/kotlin/dev/ikna/ui/session/SessionUiState.kt")
        assertTrue(state.contains("loading || loadFailed || sessionChanged"))
    }

    private fun source(relative: String): String = listOf(File(relative), File("../$relative"))
        .first { it.isFile }.readText()
}
