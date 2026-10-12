package dev.ikna.desktop

import dev.ikna.ui.onboarding.FirstStudyDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FirstStudyRouteTest {
    @Test fun bothChoicesClearStaleSessionAndDeckNavigation() {
        for ((choice, expected) in listOf(
            FirstStudyDestination.SESSION to Pane.SESSION,
            FirstStudyDestination.CATALOGUE to Pane.CATALOG
        )) {
            val ui = DesktopUi()
            ui.study("old-session")
            ui.browse("old-browse")
            ui.openDeckScreen("old-deck")
            ui.listOpen = true
            ui.showShortcuts = true
            ui.completeFirstRun(choice)
            assertEquals(expected, ui.pane)
            assertNull(ui.sessionDeck)
            assertNull(ui.browseDeck)
            assertNull(ui.openDeck)
            assertFalse(ui.listOpen)
            assertFalse(ui.showShortcuts)
        }
    }
}
