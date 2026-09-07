package dev.ikna.ui.theme

import dev.ikna.ui.SettingsSourceContracts
import org.junit.Test

/**
 * Same named JUnit cases, with assertions shared with the fast Java preflight.
 * The host still owns lazy items; shared SettingsChrome owns observation and motion.
 */
class MotionPolishTest {
    private val contracts = SettingsSourceContracts()

    @Test
    fun fast_settings_fling_does_not_start_a_competing_jump_animation() {
        contracts.fastSettingsFlingDoesNotStartACompetingJumpAnimation()
    }

    @Test
    fun auto_load_target_is_published_only_after_measurement_is_known() {
        contracts.autoLoadTargetIsPublishedOnlyAfterMeasurementIsKnown()
    }

    @Test
    fun micro_motion_is_short_local_and_obeys_the_existing_switch() {
        contracts.microMotionIsShortLocalAndObeysTheExistingSwitch()
    }
}
