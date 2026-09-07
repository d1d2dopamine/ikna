package dev.ikna.ui.settings

import dev.ikna.ui.SettingsSourceContracts
import org.junit.Test

/**
 * Same named JUnit cases, with assertions shared with the fast Java preflight.
 * The host still owns lazy items; shared SettingsChrome owns observation and motion.
 */
class SettingsLazyLayoutTest {
    private val contracts = SettingsSourceContracts()

    @Test
    fun offscreen_settings_are_not_composed_eagerly() {
        contracts.offscreenSettingsAreNotComposedEagerly()
    }

    @Test
    fun jump_strip_targets_lazy_items_without_global_section_measurement() {
        contracts.jumpStripTargetsLazyItemsWithoutGlobalSectionMeasurement()
    }

    @Test
    fun speech_engine_waits_until_its_section_is_visible() {
        contracts.speechEngineWaitsUntilItsSectionIsVisible()
    }
}
