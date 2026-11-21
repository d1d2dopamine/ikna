package dev.ikna.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.ui.decks.HomeDestination
import dev.ikna.ui.decks.IknaHomeChrome
import dev.ikna.ui.decks.IknaTodayBlock
import dev.ikna.data.repo.TodayStudySummary
import dev.ikna.ui.text.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeDockLayoutTest {
    @Test
    fun emptyTodayActionLabelSharesTheLeftEdgeOfToday() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 420, height = 300) {
            setContent {
                MaterialTheme {
                    IknaTodayBlock(TodayStudySummary(), perCardMs = null, onClick = {})
                }
            }
            val heading = onNodeWithText(S.t("deck.007"), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val action = onNodeWithText(S.t("a11y.019"), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(heading.left, action.left)
        }
    }

    @Test
    fun deckListAndDockKeepTheirHeightDuringSidebarResize() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 500, height = 500) {
            val sidebarWidth = mutableStateOf(300.dp)
            setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().testTag("sidebar-window")) {
                        DesktopDeckSidebarViewport(sidebarWidth.value, contentWidth = 300.dp) {
                            IknaHomeChrome(dockAtTop = false, leftHanded = false, onNavigate = {}) {
                                LazyColumn(Modifier.weight(1f)) {
                                    item { Text("English from context") }
                                }
                            }
                        }
                    }
                }
            }
            val viewport = onNodeWithTag("sidebar-window").getUnclippedBoundsInRoot()
            for (width in listOf(300.dp, 260.dp, 0.dp, 300.dp)) {
                runOnIdle { sidebarWidth.value = width }
                if (width == 0.dp) continue
                val deck = onNodeWithText("English from context").getUnclippedBoundsInRoot()
                val dock = onNodeWithContentDescription(S.t("a11y.011")).getUnclippedBoundsInRoot()
                assertTrue(deck.bottom > deck.top)
                assertTrue(deck.bottom < dock.top)
                assertTrue(viewport.bottom - dock.bottom < 40.dp)
            }
        }
    }

    @Test
    fun dockAndHeadingTradePlacesAndSearchStillOpens() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 300, height = 500) {
            val atTop = mutableStateOf(false)
            val fullWidth = mutableStateOf(false)
            val opened = mutableListOf<HomeDestination>()
            setContent {
                MaterialTheme {
                    IknaHomeChrome(
                        dockAtTop = atTop.value,
                        leftHanded = false,
                        fullWidthDock = fullWidth.value,
                        today = { Text("Today summary") },
                        onNavigate = { opened += it }
                    ) {
                        Box(Modifier.fillMaxWidth().weight(1f)) { Text("First deck") }
                    }
                }
            }
            val heading = onNodeWithText(S.t("deck.004"))
            val search = onNodeWithContentDescription(S.t("a11y.011"))
            assertTrue(heading.getUnclippedBoundsInRoot().bottom < search.getUnclippedBoundsInRoot().top)
            for (wideBar in listOf(false, true)) {
                runOnIdle {
                    atTop.value = true
                    fullWidth.value = wideBar
                }
                val bounds = search.getUnclippedBoundsInRoot()
                assertTrue(onNodeWithText("Today summary").getUnclippedBoundsInRoot().bottom < bounds.top)
                assertTrue(bounds.bottom < onNodeWithText("First deck").getUnclippedBoundsInRoot().top)
                assertTrue(bounds.bottom < heading.getUnclippedBoundsInRoot().top)
                search.performClick()
            }
            runOnIdle { assertEquals(List(2) { HomeDestination.SEARCH }, opened) }
        }
    }
}
