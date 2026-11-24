package dev.ikna.ui.settings

import androidx.compose.foundation.lazy.LazyListItemInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsJumpTest {
    private val sections = listOf("look", "language", "advanced")

    @Test
    fun nextSectionTakesOverWhilePreviousSectionIsStillVisible() {
        val look = Item("look", index = 0, offset = -500, size = 600)
        val language = Item("language", index = 1, offset = 100, size = 450)

        assertEquals("language", active(look, language))
        assertEquals("look", active(look.copy(size = 660), language.copy(offset = 160)))
    }

    @Test
    fun readingAnchorIsRelativeToThePaddedViewport() {
        val look = Item("look", index = 0, offset = -85, size = 165)
        val language = Item("language", index = 1, offset = 80, size = 420)

        assertEquals("language", active(look, language, viewportStart = -40, viewportEnd = 460))
    }

    @Test
    fun footerDoesNotBecomeASectionAndBottomSelectsTheLastSection() {
        val language = Item("language", index = 1, offset = -100, size = 420)
        val advanced = Item("advanced", index = 2, offset = 320, size = 70)
        val message = Item("settings-message", index = 3, offset = 390, size = 40)
        val spacer = Item("settings-bottom-space", index = 4, offset = 430, size = 70)

        assertEquals("language", active(language, advanced, message, spacer))
        assertEquals("advanced", active(language, advanced, message, spacer, canScrollForward = false))
        assertEquals("advanced", active(language, advanced, canScrollForward = false, totalItems = 3))
    }

    @Test
    fun initialEmptyLayoutSelectsTheFirstSection() {
        assertEquals("look", active(canScrollForward = false, totalItems = 0))
    }

    private fun active(
        vararg items: Item,
        viewportStart: Int = 0,
        viewportEnd: Int = 500,
        canScrollForward: Boolean = true,
        totalItems: Int = 5
    ): String = resolveActiveSettingsSectionId(
        sections, items.toList(), viewportStart, viewportEnd, totalItems, canScrollForward
    )

    private data class Item(
        override val key: String,
        override val index: Int,
        override val offset: Int,
        override val size: Int
    ) : LazyListItemInfo {
        override val contentType: Any? = null
    }
}
