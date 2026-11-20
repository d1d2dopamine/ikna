package dev.ikna.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.decks.IknaDeckRow
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class DeckRowLayoutIntegrationTest {
    @Test
    fun `title and mark share a top edge at normal and large font sizes`() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 360, height = 240) {
            val fontScale = mutableStateOf(1f)
            val title = "English from context"
            setContent {
                val density = LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                    MaterialTheme {
                        IknaDeckRow(
                            deck = DeckSummary("test", title, "en", 20, 5, 0, isActive = true),
                            look = DeckLook(label = "EN"),
                            dueToday = 5,
                            onOpen = {}
                        )
                    }
                }
            }

            for (scale in listOf(1f, 2f)) {
                runOnIdle { fontScale.value = scale }
                val mark = onNodeWithTag("ikna-deck-mark", useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                val titleBounds = onNodeWithText(title, useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                assertEquals("Mark size at font scale $scale", 52.dp, mark.bottom - mark.top)
                assertEquals("Title top at font scale $scale", mark.top, titleBounds.top)
            }
        }
    }
}
