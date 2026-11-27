package dev.ikna.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural regression checks only; repository tests cover actual answer/Undo transactions. */
class DesktopOptionalReviewContractTest {
    private val source: String
        get() = listOf(
            "desktop/src/main/kotlin/dev/ikna/desktop/SessionPane.kt",
            "src/main/kotlin/dev/ikna/desktop/SessionPane.kt"
        ).map(::File).firstOrNull(File::isFile)?.readText()
            ?: error("SessionPane source not found")

    private fun section(start: String, end: String): String {
        val text = source
        val from = text.indexOf(start)
        require(from >= 0) { "Missing section: $start" }
        val until = text.indexOf(end, from + start.length)
        require(until > from) { "Missing section end: $end" }
        return text.substring(from, until)
    }

    @Test
    fun `optional hard review UI is removed leaving only standard extra review`() {
        assertFalse(source.contains("onOptionalHard"))
        assertFalse(source.contains("onFinishOptional"))
        assertFalse(source.contains("startOptionalHardReview"))
        assertFalse(source.contains("optionalBatch"))
        assertTrue(source.contains("onExtra = addMore"))
    }

    @Test
    fun `deck identity owns complete session state and coroutine scope`() {
        val wrapper = section("fun SessionPane(", "private fun SessionPaneContent(")
        assertTrue(wrapper.contains("key(deckId) {"))
        assertTrue(wrapper.contains("SessionPaneContent(container, settings, palette, deckId,"))
        assertFalse(wrapper.contains("rememberCoroutineScope()"))
        assertTrue(section("private fun SessionPaneContent(", "LaunchedEffect(deckId, reload)")
            .contains("val scope = rememberCoroutineScope()"))
    }

    @Test
    fun `answer failure cannot advance or advertise successful Undo`() {
        val grade = section("val gradeWithSignals:", "val requestKeyboardSwipe:")
        val success = grade.substringAfter("if (result.isSuccess) {").substringBefore("} else {")
        assertTrue(success.contains("answeredHere += 1"))
        assertTrue(success.contains("advance()"))
        val failure = grade.substringAfter("logLine(\"answer failed:")
        assertTrue(failure.contains("loadFailed = true"))
        assertTrue(failure.contains("undoVisible = false"))
        assertFalse(failure.contains("advance()"))
        assertFalse(failure.contains("answeredHere += 1"))
    }

    @Test
    fun `answer Undo and addMore reserve saving before launch`() {
        for (block in listOf(
            section("val gradeWithSignals:", "val requestKeyboardSwipe:"),
            section("val undo:", "val addMore:"),
            section("val addMore:", "val wrong:")
        )) {
            assertTrue(block.substringBefore("scope.launch {").contains("!saving"))
            assertTrue(block.substringBefore("scope.launch {").contains("saving = true"))
        }
    }
}
