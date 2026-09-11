package dev.ikna.ui.decks

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileDeckUiContractTest {
    @Test
    fun `mobile new deck screen keeps actions and drops duplicate prose`() {
        val source = androidSource("AddDeckScreen.kt")
        assertFalse(source.contains("S.t(\"add.002\")"))
        assertFalse(source.contains("S.t(\"anki.025\")"))

        val order = listOf(
            "S.t(\"cat.031\")",
            "S.t(\"cat.033\")",
            "S.t(\"cat.032\")",
            "S.t(\"add.072\")",
            "S.t(\"add.016\")",
            "S.t(\"add.015\")",
            "S.t(\"anki.001\")",
            "S.t(\"anki.026\")"
        ).map { token -> source.indexOf(token) }
        assertTrue(order.all { it >= 0 })
        assertTrue(order.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun `shared row restores the compact mark and bounds every line to it`() {
        val android = androidSource("DecksScreen.kt")
        val shared = sharedSource("DeckList.kt")

        assertTrue(android.contains("IknaDeckRow("))
        assertFalse(android.contains("private fun DeckRow("))
        assertFalse(android.contains("private fun DeckMark("))
        for (required in listOf(
            "private val DECK_ROW_HEIGHT = 52.dp",
            "private val DECK_MARK_SIZE = 52.dp",
            "private val DECK_INFO_HEIGHT = 34.dp",
            "private val DECK_PROGRESS_HEIGHT = 14.dp",
            ".height(DECK_ROW_HEIGHT)\n            .clipToBounds()",
            "modifier = Modifier.weight(1f).fillMaxHeight()",
            "modifier = Modifier.height(DECK_INFO_HEIGHT)",
            "verticalArrangement = Arrangement.SpaceBetween",
            "Spacer(Modifier.height(Space.xs))\n                IknaDeckProgress(",
            "modifier = Modifier.height(DECK_PROGRESS_HEIGHT)"
        )) assertTrue(required, shared.contains(required))

        val row = shared.substringAfter("fun IknaDeckRow(")
            .substringBefore("/** Makes the long-term bar")
        assertFalse(row.contains("68.dp"))
        assertTrue(row.contains("style = MaterialTheme.typography.labelSmall"))
        assertTrue(row.split("overflow = TextOverflow.Ellipsis").size >= 3)
    }

    private fun androidSource(name: String): String = source(
        "app/src/main/java/dev/ikna/ui/decks/$name",
        "src/main/java/dev/ikna/ui/decks/$name"
    )

    private fun sharedSource(name: String): String = source(
        "shared/src/jvmShared/kotlin/dev/ikna/ui/decks/$name",
        "../shared/src/jvmShared/kotlin/dev/ikna/ui/decks/$name"
    )

    private fun source(vararg candidates: String): String = candidates.asSequence()
        .map(::File)
        .firstOrNull(File::isFile)
        ?.readText()
        ?: error("source not found: ${candidates.joinToString()}")
}
