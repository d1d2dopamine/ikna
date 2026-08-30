package dev.ikna.data.anki

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release contracts that keep the first Anki bridge safe and honest. */
class AnkiBridgeContractTest {
    private val root = File(System.getProperty("user.dir")).let { here ->
        if (File(here, "src/main").isDirectory) here else File(here, "app")
    }

    @Test
    fun `package import is transactional and never executes card code`() {
        val source = File(root, "src/main/java/dev/ikna/data/anki/AnkiImporter.kt").readText()
        assertTrue(source.contains("db.inTransaction"))
        assertTrue(source.contains("SQLiteDatabase.OPEN_READONLY"))
        assertTrue(source.contains("MAX_PACKAGE_BYTES"))
        assertFalse(source.contains("evaluateJavascript"))
        assertFalse(source.contains("WebView("))
    }

    @Test
    fun `all imported history is replayed by the existing fsrs path`() {
        val source = File(root, "src/main/java/dev/ikna/data/anki/AnkiImporter.kt").readText()
        assertTrue(source.contains("restore.restoreFromJsonl"))
        assertTrue(source.contains("ReviewRecord"))
        assertTrue(source.contains("prevIsNew"))
    }

    @Test
    fun `stable ids make reimport idempotent`() {
        assertTrue(AnkiImporter.stableChunkId(42, 7) == AnkiImporter.stableChunkId(42, 7))
        assertTrue(AnkiImporter.stablePackId(42, 9) == "anki-42-deck-9")
    }

    @Test
    fun `return copy has no recovery countdown`() {
        val files = listOf("Ru", "En", "Pl", "Es", "Fr", "De").map {
            File(root.parentFile, "shared/src/jvmShared/kotlin/dev/ikna/ui/text/Strings$it.kt")
        }
        files.forEach { file ->
            val value = Regex("\"sess\\.027\"\\s+to\\s+\"([^\"]*)\"")
                .find(file.readText())?.groupValues?.get(1).orEmpty()
            assertTrue(file.name + " has no return copy", value.isNotBlank())
            assertFalse(file.name + " exposes a countdown", Regex("\\d").containsMatchIn(value))
        }
    }
}
