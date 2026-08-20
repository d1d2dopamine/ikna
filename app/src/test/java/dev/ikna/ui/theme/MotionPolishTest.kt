package dev.ikna.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolishTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source was not found: $relative")
    }

    @Test
    fun fast_settings_fling_does_not_start_a_competing_jump_animation() {
        val settings = source("dev/ikna/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("private fun SettingsJumpRow("))
        assertTrue(settings.contains("derivedStateOf { listState.isScrollInProgress }"))
        assertTrue(settings.contains("verticalScrolling || rowWidth == 0"))
        assertTrue(settings.contains("SettingsJumpRow("))
        assertTrue(settings.contains("listState = listState"))
        assertFalse(settings.contains("return@JumpRow"))
        assertEquals(
            9,
            settings.lineSequence().count { line ->
                line.contains("item(key = ID_") &&
                    line.contains("contentType = SETTINGS_SECTION_CONTENT_TYPE")
            }
        )
    }

    @Test
    fun auto_load_target_is_published_only_after_measurement_is_known() {
        val settings = source("dev/ikna/ui/settings/SettingsScreen.kt")
        assertTrue(settings.contains("mutableStateOf<Int?>(null)"))
        assertTrue(settings.contains("val measured = container.learningRepository.normIsMeasured()"))
        assertTrue(settings.contains("measuredNorm = target.takeIf { measured && it > 0 }"))
        assertFalse(settings.contains("var normMeasured"))
    }

    @Test
    fun micro_motion_is_short_local_and_obeys_the_existing_switch() {
        val metrics = source("dev/ikna/ui/theme/Metrics.kt")
        val theme = source("dev/ikna/ui/theme/Theme.kt")
        val main = source("dev/ikna/MainActivity.kt")
        val flat = source("dev/ikna/ui/theme/Flat.kt")
        val settings = source("dev/ikna/ui/settings/SettingsScreen.kt")
        assertTrue(metrics.contains("LocalIknaMotionEnabled"))
        assertTrue(metrics.contains("controlChangeDurationMillis = 160"))
        assertTrue(metrics.contains("contentChangeDurationMillis = 200"))
        assertTrue(metrics.contains("progressChangeDurationMillis = 260"))
        assertTrue(theme.contains("LocalIknaMotionEnabled provides motionEnabled"))
        assertTrue(main.contains("motionEnabled = settings.animations"))
        assertTrue(flat.contains("animateDpAsState("))
        assertTrue(flat.contains("animateColorAsState("))
        assertTrue(flat.contains("animateFloatAsState("))
        assertTrue(settings.contains("Modifier.animateContentSize("))
    }
}
