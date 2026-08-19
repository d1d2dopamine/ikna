package dev.ikna.ui.settings

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLazyLayoutTest {
    private fun source(): String {
        val candidates = listOf(
            File("src/main/java/dev/ikna/ui/settings/SettingsScreen.kt"),
            File("app/src/main/java/dev/ikna/ui/settings/SettingsScreen.kt")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("SettingsScreen.kt was not found")
    }

    @Test
    fun offscreen_settings_are_not_composed_eagerly() {
        val text = source()
        assertTrue(text.contains("LazyColumn("))
        assertTrue(text.contains("rememberLazyListState()"))
        assertFalse(text.contains(".verticalScroll("))
        assertFalse(text.contains("private fun Anchored("))
        assertEquals(9, Regex("item\\(key = ID_").findAll(text).count())
    }

    @Test
    fun jump_strip_targets_lazy_items_without_global_section_measurement() {
        val text = source()
        assertTrue(text.contains("firstVisibleItemIndex"))
        assertTrue(text.contains("animateScrollToItem(targetIndex)"))
        assertTrue(text.contains("scrollToItem(targetIndex)"))
        assertFalse(text.contains("Anchored(ID_"))
    }

    @Test
    fun speech_engine_waits_until_its_section_is_visible() {
        val text = source()
        assertTrue(text.contains("speechSectionVisible"))
        assertTrue(text.contains("!settings.speechEnabled || !speechSectionVisible"))
    }
}
