package dev.ikna

import androidx.compose.animation.core.TweenSpec
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.ReadableWidth
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.TouchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid is a promise the whole interface makes, and a promise that is only
 * kept by hand gets broken the first time someone is in a hurry. These are the
 * rules a screen cannot quietly drift away from.
 */
class GridTest {

    private val scale = listOf(Space.xs, Space.sm, Space.md, Space.lg, Space.xl, Space.xxl)

    @Test
    fun every_step_sits_on_the_four_point_grid() {
        scale.forEach { step ->
            assertEquals(step.toString(), 0f, step.value % 4f, 0.001f)
        }
    }

    @Test
    fun the_steps_ascend_and_never_repeat() {
        val values = scale.map { it.value }
        assertEquals(values.sorted(), values)
        assertEquals(values.toSet().size, values.size)
    }

    @Test
    fun a_hairline_is_a_hairline() {
        // The one value off the grid, because it is a line rather than a space.
        assertEquals(1f, Space.hair.value, 0.001f)
    }

    @Test
    fun the_screen_margin_is_a_step_on_the_scale() {
        assertTrue(scale.contains(Edge))
    }

    @Test
    fun touch_targets_clear_the_accessibility_floor() {
        assertTrue(TouchTarget.value >= 44f)
    }

    @Test
    fun reading_width_is_capped_but_never_bites_on_a_phone() {
        assertTrue(ReadableWidth.value >= 480f)
        assertTrue(ReadableWidth.value <= 720f)
    }

    @Test
    fun a_harder_throw_leaves_the_screen_faster() {
        val gentle = (Motion.thrown(0f) as TweenSpec<Float>).durationMillis
        val hard = (Motion.thrown(6000f) as TweenSpec<Float>).durationMillis
        assertEquals(220, gentle)
        assertEquals(120, hard)
        assertTrue(hard < gentle)
    }

    @Test
    fun throw_duration_stays_inside_its_bounds() {
        listOf(-9000f, 0f, 100f, 1200f, 99999f).forEach { speed ->
            val ms = (Motion.thrown(speed) as TweenSpec<Float>).durationMillis
            assertTrue(speed.toString(), ms in 120..220)
        }
    }
}
