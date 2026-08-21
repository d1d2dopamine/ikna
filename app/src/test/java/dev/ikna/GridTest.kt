package dev.ikna

import androidx.compose.animation.core.TweenSpec
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.ReadableWidth
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.TouchTarget
import dev.ikna.ui.theme.segmentFill
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

    /**
     * A kept card and a lost card must not leave the screen at the same speed.
     * This is the whole of the "motion says something" claim, reduced to the one
     * thing that can be checked without a device: the same throw, weighed
     * differently, produces different durations, and the light one is quicker.
     */
    @Test
    fun a_kept_card_leaves_lighter_than_a_lost_one() {
        listOf(0f, 900f, 6000f).forEach { speed ->
            val kept = (Motion.thrown(speed, haste = 1.25f) as TweenSpec<Float>).durationMillis
            val lost = (Motion.thrown(speed, haste = 0.58f) as TweenSpec<Float>).durationMillis
            val plain = (Motion.thrown(speed) as TweenSpec<Float>).durationMillis
            assertTrue(speed.toString(), kept < plain)
            assertTrue(speed.toString(), plain < lost)
        }
    }

    @Test
    fun weight_never_stalls_or_snaps_a_card() {
        listOf(0f, 500f, 99999f).forEach { speed ->
            listOf(0f, 0.58f, 0.72f, 1f, 1.25f, 1.55f).forEach { haste ->
                val ms = (Motion.thrown(speed, haste) as TweenSpec<Float>).durationMillis
                assertTrue("$speed/$haste", ms in 90..340)
            }
        }
    }

    @Test
    fun shared_axis_x_is_short_subtle_and_completes_its_fades() {
        assertEquals(280, Motion.sharedAxisDurationMillis)
        assertEquals(14f, Motion.sharedAxisTravel.value, 0.001f)
        assertEquals(90, Motion.sharedAxisFadeInDelayMillis)
        assertEquals(190, Motion.sharedAxisFadeInDurationMillis)
        assertEquals(90, Motion.sharedAxisFadeOutDurationMillis)
        assertEquals(180, Motion.sectionScrollDurationMillis)
        assertTrue(
            Motion.sharedAxisFadeInDelayMillis + Motion.sharedAxisFadeInDurationMillis <=
                Motion.sharedAxisDurationMillis
        )
        assertEquals(
            Motion.sharedAxisFadeOutDurationMillis,
            Motion.sharedAxisFadeInDelayMillis
        )
        assertTrue(Motion.sharedAxisFadeOutDurationMillis < Motion.sharedAxisDurationMillis)
    }

    @Test
    fun segmented_progress_keeps_full_and_partial_memory_cells() {
        assertEquals(0, segmentFill(0f, 18).complete)
        assertEquals(9, segmentFill(0.5f, 18).complete)
        assertEquals(18, segmentFill(1f, 18).complete)
        assertEquals(0f, segmentFill(1f, 18).partial, 0.0001f)

        val partial = segmentFill(0.51f, 18)
        assertEquals(9, partial.complete)
        assertTrue(partial.partial > 0f)
        assertTrue(partial.partial < 1f)

        assertEquals(0, segmentFill(Float.NaN, 18).complete)
    }

    /**
     * One card of six hundred is a real thing that happened, and the bar beside
     * it says "0%". A hair of a cell there contradicts the number, so under one
     * percent the bar draws nothing at all.
     */
    @Test
    fun segmented_progress_draws_nothing_below_one_percent() {
        val hair = segmentFill(0.004f, 18)
        assertEquals(0, hair.complete)
        assertEquals(0f, hair.partial, 0.0001f)

        val visible = segmentFill(0.02f, 18)
        assertEquals(0, visible.complete)
        assertTrue(visible.partial > 0f)
    }
}
