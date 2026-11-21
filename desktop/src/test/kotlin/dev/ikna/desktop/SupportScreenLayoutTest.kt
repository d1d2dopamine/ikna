package dev.ikna.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.ui.decks.IknaSupportScreen
import dev.ikna.ui.text.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SupportScreenLayoutTest {
    @Test
    fun supportDestinationsShareARowAndOpenTheCorrectAddresses() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 460, height = 600) {
            val visited = mutableListOf<String>()
            val handler = object : UriHandler {
                override fun openUri(uri: String) {
                    visited += uri
                }
            }
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalUriHandler provides handler) {
                        IknaSupportScreen(installedVersion = "0.12.0", onBack = {})
                    }
                }
            }
            val tiles = listOf("support.repo", "support.issue", "support.mail", "support.author", "support.site")
                .map { onNodeWithText(S.t(it)) }
            onNodeWithText(S.t("upd.011") + "0.12.0").assertExists()
            onNodeWithText(S.t("support.changelog")).performClick()
            val bounds = tiles.map { it.getUnclippedBoundsInRoot() }
            bounds.zipWithNext().forEach { (left, right) ->
                assertTrue(left.right < right.left)
                assertTrue(left.top < right.bottom && right.top < left.bottom)
            }
            for (tile in tiles) tile.performClick()
            runOnIdle {
                assertEquals(
                    listOf(
                        "https://github.com/d1d2dopamine/ikna/releases",
                        "https://github.com/d1d2dopamine/ikna",
                        "https://github.com/d1d2dopamine/ikna/issues/new/choose",
                        "mailto:d1d2dopamine@gmail.com",
                        "https://github.com/d1d2dopamine",
                        "https://d1d2dopamine.is-a.dev/"
                    ),
                    visited
                )
            }
        }
    }
}
