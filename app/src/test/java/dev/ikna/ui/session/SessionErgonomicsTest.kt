package dev.ikna.ui.session

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release contracts for the small review-session polish in 0.8.0.
 *
 * These are source contracts because both behaviours sit at the Compose/Android
 * boundary: the JVM unit runner has neither a real View nor a semantics tree.
 */
class SessionErgonomicsTest {

    @Test
    fun `the display is awake only while a card is active`() {
        val screen = source("dev/ikna/ui/session/SessionScreen.kt")

        assertTrue(screen.contains("val keepScreenAwake = card != null && !state.loading && !state.finished"))
        assertTrue(screen.contains("DisposableEffect(view, keepScreenAwake)"))
        assertTrue(screen.contains("view.keepScreenOn = previous || keepScreenAwake"))
        assertTrue(screen.contains("onDispose { view.keepScreenOn = previous }"))
        assertFalse(screen.contains("FLAG_KEEP_SCREEN_ON"))
    }

    @Test
    fun `accessibility cannot answer an unrevealed card`() {
        val stack = source("dev/ikna/ui/session/CardStack.kt")

        assertTrue(stack.contains("customActions = if (revealed)"))
        assertTrue(stack.contains("CustomAccessibilityAction(revealAction)"))
        assertTrue(stack.contains("val canReveal = !revealedNow.value"))
        assertTrue(stack.contains("val canRate = revealedNow.value"))
        assertTrue(stack.contains("rateNow.value(Rating.GOOD, signals.snapshot())"))
        assertTrue(stack.contains("rateNow.value(Rating.AGAIN, signals.snapshot())"))
    }

    @Test
    fun `optional hard review is explicit and uses separate finite answer and undo paths`() {
        val screen = source("dev/ikna/ui/session/SessionScreen.kt")
        val vm = source("dev/ikna/ui/session/SessionViewModel.kt")
        assertTrue(screen.contains("onOptionalHard = vm::startOptionalHardReview"))
        assertTrue(screen.contains("onFinishOptional = vm::finishOptionalHardReview"))
        val start = vm.substringAfter("fun startOptionalHardReview()").substringBefore("fun finishOptionalHardReview()")
        assertTrue(start.contains("!s.finished || !s.optionalHardAvailable"))
        assertTrue(start.contains("repo.startOptionalHardReview(deckId)"))
        assertFalse(start.contains("buildSession"))
        val rate = vm.substringAfter("fun rate(").substringAfter("optionalBatch?.let { batch ->").substringBefore("answeredKeys += card.card.key")
        assertTrue(rate.contains("repo.answerOptionalHard(batch, card, rating, duration, now, signals)"))
        assertTrue(rate.contains("val next = s.index + 1"))
        assertTrue(rate.contains("goalAnswersToday = repo.goalAnswersToday(now)"))
        assertFalse(rate.contains("s.queue + card"))
        val undo = vm.substringAfter("val restored = repo.undoOptionalHard(batch)").substringBefore("val key = repo.undoLast()")
        assertTrue(undo.contains("queue = listOf(restored) + tail"))
        assertFalse(undo.contains("buildSession"))
        assertTrue(undo.contains("goalAnswersToday = repo.goalAnswersToday()"))
        assertTrue(vm.contains("optionalBatch = null"))
        assertTrue(vm.contains("failedLoad().copy(optionalHardReview = false"))
    }

    private fun source(relative: String): String {
        // Two modules, because the card stack moved.
        //
        // SwipeableCard is now compiled once in :shared and used by both the
        // phone and the desktop window, so the file it is read from is no
        // longer under app/. The contract it is read for has not changed: the
        // accessibility actions still have to depend on whether the card has
        // been revealed, on whichever machine is showing it.
        val roots = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("shared/src/jvmShared/kotlin"),
            File("../shared/src/jvmShared/kotlin")
        )
        return roots.asSequence()
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("source not found: $relative")
    }
}
