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

    @Test
    fun `empty replacement batch reloads normal counters before showing completion`() {
        val pane = source
        val start = pane.substringAfter("val startOptional: () -> Unit = {")
            .substringBefore("val finishOptional: () -> Unit = {")
        val empty = start.substringAfter("} else {").substringBefore("onFailure =")
        assertTrue(empty.contains("undoVisible = false"))
        assertTrue(empty.contains("loading = true"))
        assertTrue(empty.contains("reload += 1"))
        assertTrue(start.contains("noMoreExtra = false"))
    }

    private fun section(start: String, end: String): String {
        val text = source
        val from = text.indexOf(start)
        require(from >= 0) { "Missing section: $start" }
        val until = text.indexOf(end, from + start.length)
        require(until > from) { "Missing section end: $end" }
        return text.substring(from, until)
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
    fun `last optional answer advances finite batch without ordinary reload`() {
        val advance = section("val advance:", "val gradeWithSignals:")
        assertTrue(advance.contains("if (optionalBatch != null || index + 1 < cards.size) index += 1"))
        assertTrue(advance.contains("else { loading = true; reload += 1 }"))
        assertTrue(source.contains("val cards = if (optionalBatch != null) optionalCards else plan?.cards.orEmpty()"))
    }

    @Test
    fun `optional Undo replaces card with fresh repository result and stays in batch`() {
        val optionalUndo = section("val undo:", "val addMore:")
            .substringBefore("} else {")
        assertTrue(optionalUndo.contains("undoOptionalHard(batch)"))
        assertTrue(optionalUndo.contains("if (restored != null)"))
        assertTrue(optionalUndo.contains("optionalCards.toMutableList().also { it[previous] = restored }"))
        assertTrue(optionalUndo.contains("index = previous"))
        assertFalse(optionalUndo.contains("undoLast()"))
        assertFalse(optionalUndo.contains("reload += 1"))
        assertFalse(optionalUndo.contains("buildSession("))
    }

    @Test
    fun `ordinary reload and add more cancel batch and dismiss its Undo`() {
        for (block in listOf(
            section("LaunchedEffect(deckId, reload)", "val cards ="),
            section("val addMore:", "val startOptional:")
        )) {
            val cancellation = block.substringAfter("optionalBatch?.let {").substringBefore("optionalBatch = null")
            assertTrue(cancellation.contains("cancelOptionalHardReview(it)"))
            assertTrue(cancellation.contains("undoVisible = false"))
            assertTrue(cancellation.contains("undoFailed = false"))
            assertTrue(block.contains("optionalBatch = null"))
        }
        val finish = section("val finishOptional:", "val wrong:")
        assertTrue(finish.contains("if (!loading && !saving && acceptsInput() && optionalBatch != null)"))
        assertTrue(finish.contains("loading = true"))
        assertTrue(finish.contains("reload += 1"))
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
    fun `answer Undo and optional start reserve saving before launch`() {
        for (block in listOf(
            section("val gradeWithSignals:", "val requestKeyboardSwipe:"),
            section("val undo:", "val addMore:"),
            section("val startOptional:", "val finishOptional:")
        )) {
            assertTrue(block.substringBefore("scope.launch {").contains("!saving"))
            assertTrue(block.substringBefore("scope.launch {").contains("saving = true"))
        }
    }
}
