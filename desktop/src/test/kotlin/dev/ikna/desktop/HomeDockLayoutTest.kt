package dev.ikna.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.ui.decks.HomeDestination
import dev.ikna.ui.decks.IknaHomeChrome
import dev.ikna.ui.text.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeDockLayoutTest {
    @Test
    fun dockAndHeadingTradePlacesAndSearchStillOpens() {
        BundledSQLiteDriver().open(":memory:").close()
        runDesktopComposeUiTest(width = 300, height = 500) {
            val atTop = mutableStateOf(false)
            val opened = mutableListOf<HomeDestination>()
            setContent {
                MaterialTheme {
                    IknaHomeChrome(dockAtTop = atTop.value, leftHanded = false, onNavigate = { opened += it }) {
                        Box(Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
            val heading = onNodeWithText(S.t("deck.004"))
            val search = onNodeWithContentDescription(S.t("a11y.011"))
            assertTrue(heading.getUnclippedBoundsInRoot().bottom < search.getUnclippedBoundsInRoot().top)
            runOnIdle { atTop.value = true }
            assertTrue(search.getUnclippedBoundsInRoot().bottom < heading.getUnclippedBoundsInRoot().top)
            search.performClick()
            runOnIdle { assertEquals(listOf(HomeDestination.SEARCH), opened) }
        }
    }
}
