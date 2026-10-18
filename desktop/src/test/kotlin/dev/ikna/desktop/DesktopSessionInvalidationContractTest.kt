package dev.ikna.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source wiring checks, not physical desktop interaction or Compose lifecycle acceptance. */
class DesktopSessionInvalidationContractTest {
    private fun source(name: String): String = listOf("desktop/src/main/kotlin/dev/ikna/desktop/$name.kt",
        "src/main/kotlin/dev/ikna/desktop/$name.kt").map(::File).first(File::isFile).readText()

    @Test fun `committed merge callback only signals presentation generation`() {
        val container = source("DesktopContainer")
        assertTrue(container.contains("val sessionChanges = dev.ikna.domain.session.CommittedSessionChanges()"))
        assertTrue(container.contains("onMerged = { sessionChanges.notifyCommitted() }"))
    }

    @Test fun `automatic observation is read only and waits for accepted writes`() {
        val pane = source("SessionPane")
        val observer = pane.substringAfter("LaunchedEffect(committedGeneration, windowFocused, saving, loading)")
            .substringBefore("val cards =")
        assertTrue(observer.contains("if (saving || loading"))
        assertTrue(observer.contains("inspectSession(deckId)"))
        assertFalse(observer.contains("buildSession("))
        assertFalse(observer.contains("addExtra("))
        assertTrue(observer.contains("presentationGate.changed(snapshot.observation)"))
        assertTrue(observer.contains("SessionPlanChange.Stable"))
        assertTrue(observer.contains("optionalBatch = null"))
        assertTrue(observer.contains("undoVisible = false"))
    }

    @Test fun `raw commit generation blocks input and stale action publication`() {
        val pane = source("SessionPane")
        assertTrue(pane.contains("container.sessionChanges.generation.value == observedGeneration"))
        assertTrue(pane.contains("val current = if (loading || loadFailed || invalidated || sessionChanged || inspecting) null"))
        assertTrue(pane.contains("if (container.sessionChanges.generation.value != generation) return false"))
        assertTrue(pane.contains("rememberActionObservation(actionGeneration, if (optionalBatch == null) SessionPlanChange.AppendExtras else SessionPlanChange.Stable)"))
        assertTrue(pane.contains("rememberActionObservation(actionGeneration, SessionPlanChange.RemoveTarget(card.chunk.id))"))
        assertTrue(pane.contains("if (!rememberActionObservation(actionGeneration)) { saving = false; return@launch }"))
    }

    @Test fun `missing or replaced plans expose explicit Continue rather than automatic admission`() {
        val pane = source("SessionPane")
        assertTrue(pane.contains("sessionChanged = snapshot.plan == null || !SessionObservationPolicy.compatible"))
        assertTrue(pane.contains("IknaSessionChanged(onContinue = {"))
        assertTrue(pane.contains("if (!saving) { loading = true; reload += 1 }"))
        assertTrue(pane.contains("val windowFocused = LocalWindowInfo.current.isWindowFocused"))
        val load = pane.substringAfter("LaunchedEffect(deckId, reload)").substringBefore("// This observer")
        assertTrue(load.indexOf("if (generation != observedGeneration)") < load.indexOf("buildSession(deckId = deckId)"))
        assertTrue(load.contains("plan = it.plan"))
    }
    @Test fun `forced practice exception requires explicit developer sandbox load`() {
        val pane = source("SessionPane")
        val load = pane.substringAfter("LaunchedEffect(deckId, reload)").substringBefore("// This observer")
        assertTrue(load.contains("if (container.isDeveloperMode && loaded.forcedByDeveloper) snapshot.copy(plan = loaded) else snapshot"))
        val observer = pane.substringAfter("LaunchedEffect(committedGeneration, windowFocused, saving, loading)")
            .substringBefore("val cards =")
        assertFalse(observer.contains("forcedByDeveloper"))
        assertFalse(observer.contains("buildSession("))
    }

}
