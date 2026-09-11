package dev.ikna.desktop

import dev.ikna.data.prefs.HotkeyChord
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyRegressionTest {
    @Test
    fun `hexadecimal-looking Latin shortcuts stay visible`() {
        for (letter in listOf("A", "B", "C", "D", "E", "F")) {
            val chord = requireNotNull(HotkeyChord.parse(letter))
            assertEquals(letter, hotkeyDisplay(chord))
        }
        val unicode = requireNotNull(HotkeyChord.parse("CHAR_0416"))
        assertEquals("Ж", hotkeyDisplay(unicode))
    }

    @Test
    fun `pointer reveal returns focus and does not lose a queued answer`() {
        val session = source(
            "desktop/src/main/kotlin/dev/ikna/desktop/SessionPane.kt",
            "src/main/kotlin/dev/ikna/desktop/SessionPane.kt"
        )
        val stack = source(
            "shared/src/jvmShared/kotlin/dev/ikna/ui/session/CardStack.kt",
            "../shared/src/jvmShared/kotlin/dev/ikna/ui/session/CardStack.kt"
        )

        assertTrue(session.contains("LaunchedEffect(revealed, current?.card?.key)"))
        assertTrue(session.contains("if (revealed) runCatching { focus.requestFocus() }"))
        assertTrue(stack.contains("snapshotFlow { flying.value }.first { active -> !active }"))
        assertTrue(stack.countOccurrences("if (!revealedNow.value)") >= 2)
    }

    private fun source(vararg candidates: String): String = candidates.asSequence()
        .map(::File)
        .firstOrNull(File::isFile)
        ?.readText()
        ?: error("source not found: ${candidates.joinToString()}")

    private fun String.countOccurrences(needle: String): Int =
        windowed(needle.length).count { it == needle }
}
