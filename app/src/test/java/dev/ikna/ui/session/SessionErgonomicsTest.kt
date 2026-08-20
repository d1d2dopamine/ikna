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
        assertTrue(stack.contains("rateNow.value(Rating.GOOD)"))
        assertTrue(stack.contains("rateNow.value(Rating.AGAIN)"))
    }

    private fun source(relative: String): String {
        val roots = listOf(File("app/src/main/java"), File("src/main/java"))
        return roots.asSequence()
            .map { File(it, relative) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("source not found: $relative")
    }
}
