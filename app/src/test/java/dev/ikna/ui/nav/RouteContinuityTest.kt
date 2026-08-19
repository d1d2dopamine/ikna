package dev.ikna.ui.nav

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteContinuityTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source was not found: $relative")
    }

    @Test
    fun home_data_and_scroll_live_above_the_destination_composition() {
        val decks = source("dev/ikna/ui/decks/DecksScreen.kt")
        val nav = source("dev/ikna/ui/nav/IknaNavHost.kt")
        assertTrue(decks.contains("class DecksHomeState"))
        assertFalse(decks.contains("var decks by remember"))
        assertTrue(decks.contains("state = listState"))
        assertTrue(nav.contains("remember { DecksHomeState() }"))
        assertTrue(nav.contains("rememberLazyListState()"))
        assertTrue(nav.contains("state = decksState"))
        assertTrue(nav.contains("listState = decksListState"))
    }

    @Test
    fun home_grain_cannot_leak_into_settings() {
        val decks = source("dev/ikna/ui/decks/DecksScreen.kt")
        val nav = source("dev/ikna/ui/nav/IknaNavHost.kt")
        val settings = source("dev/ikna/ui/settings/SettingsScreen.kt")
        assertTrue(nav.contains("currentBackStackEntryAsState()"))
        assertTrue(nav.contains("showMemoryField = currentRoute == null || currentRoute == Routes.HOME"))
        assertTrue(decks.contains("if (showMemoryField)"))
        assertTrue(nav.contains(".background(MaterialTheme.colorScheme.background)"))
        assertTrue(nav.contains(".clipToBounds()"))
        assertTrue(settings.contains(".background(MaterialTheme.colorScheme.background)"))
    }
}
