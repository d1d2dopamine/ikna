package dev.ikna.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/*
 * The grid, and how things move on it.
 *
 * Before this file the interface used twenty different gaps — 6, 7, 10, 14, 18,
 * 22, 30, 38 — chosen one at a time, each of them defensible on its own screen
 * and none of them agreeing with the next. Nobody sees a single spacing value.
 * Everybody sees the regularity, or its absence: an interface off the grid reads
 * as homemade even when no individual screen is wrong.
 *
 * Everything below is a multiple of four. Four is small enough that no layout has
 * to fight it and large enough that the steps stay distinguishable, and the whole
 * scale is short on purpose — six values, so choosing is instant and a screen
 * cannot drift.
 */
object Space {
    /** Rules and borders. The only value not on the grid, because it is a line. */
    val hair = 1.dp

    /** Between a label and the thing it labels. */
    val xs = 4.dp

    /** Inside a row. */
    val sm = 8.dp

    /** Between related rows. */
    val md = 12.dp

    /** Screen margin, and between unrelated rows. */
    val lg = 20.dp

    /** Between sections. */
    val xl = 32.dp

    /** Around something that is alone on the screen. */
    val xxl = 48.dp
}

/** The screen margin. One value, every screen, so the left edge never jumps. */
val Edge = Space.lg

/** Nothing pressable is smaller than this. */
val TouchTarget = 44.dp

/** Top bars. */
val BarHeight = 56.dp

/**
 * Reading width.
 *
 * Text that runs the full width of a tablet is text nobody finishes: the eye
 * loses the start of the next line. Phones never reach this, so it costs nothing
 * where it does not apply.
 */
val ReadableWidth = 560.dp

fun Modifier.readable(): Modifier = this.widthIn(max = ReadableWidth)

/**
 * How the app moves.
 *
 * Two rules. Anything the finger is holding follows a spring, because a spring is
 * what a hand expects from an object with weight — the old card came back to
 * centre on a 180ms straight line, which is the motion of something being pulled
 * on a string. Anything leaving the screen accelerates away and does not bounce,
 * because it is gone and a bounce would invite the eye to follow it.
 *
 * Nothing here loops, pulses or breathes. Motion in this app is feedback, and
 * feedback that repeats itself is decoration.
 */
/**
 * Whether restrained interface feedback may animate. The app-level setting is
 * provided once by IknaTheme, so primitives never invent their own accessibility
 * policy and an in-flight transition snaps to its destination when motion is off.
 */
val LocalIknaMotionEnabled = staticCompositionLocalOf { true }

object Motion {
    /** A complete Shared Axis X route change. */
    const val sharedAxisDurationMillis = 280

    /** Incoming content waits until the outgoing route is fully clear. */
    const val sharedAxisFadeInDelayMillis = 90

    /** The new route reaches full opacity exactly as its translation finishes. */
    const val sharedAxisFadeInDurationMillis = 190

    /** The old route owns the first phase, then yields the surface completely. */
    const val sharedAxisFadeOutDurationMillis = 90

    /** Small enough to read as continuity rather than as a full-screen carousel. */
    val sharedAxisTravel = 14.dp

    /** Settings section jumps use the same calm rhythm, not navigation geometry. */
    const val sectionScrollDurationMillis = 180

    /** Switches, chips and enabled states acknowledge a deliberate tap. */
    const val controlChangeDurationMillis = 160

    /** Conditional settings content changes shape without jumping. */
    const val contentChangeDurationMillis = 200

    /** Progress follows real work while filtering noisy per-chunk updates. */
    const val progressChangeDurationMillis = 260

    /** Back to rest, under the finger. Slightly underdamped: it has weight. */
    val settle: AnimationSpec<Float> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f)

    /** A new card taking its place. Barely visible, and that is the point. */
    val arrive: AnimationSpec<Float> =
        spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMedium, visibilityThreshold = 0.001f)

    /** A value that just changed and wants to be noticed once. */
    val reveal: AnimationSpec<Float> =
        spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessLow, visibilityThreshold = 0.001f)

    /**
     * A card that has been thrown.
     *
     * The duration comes from how hard it was thrown: a flick leaves fast, a slow
     * deliberate push takes its time. Same gesture, same physics, so the screen
     * agrees with the hand instead of playing a fixed animation over it.
     *
     * [haste] is what the answer weighs. A card you kept leaves the screen like
     * something light being tossed aside; a card you lost leaves like something
     * heavy being set back down. Both are the same swipe as far as the code that
     * schedules them is concerned, and the hand can still tell them apart with
     * the eyes half closed — which is the point of having a character at all.
     * Above 1 the card is quicker than the throw, below 1 it drags.
     */
    fun thrown(speedPxPerSecond: Float, haste: Float = 1f): AnimationSpec<Float> {
        val speed = max(abs(speedPxPerSecond), 0f)
        // The throw decides this much on its own, and this part is unchanged: with
        // no weight asked for, a flick still leaves in 120ms and a slow push in
        // 220.
        val base = (220f - speed / 45f).coerceIn(120f, 220f)
        val scale = if (haste <= 0f) 1f else haste
        val millis = (base / scale).coerceIn(90f, 340f).toInt()
        return tween(durationMillis = millis, easing = FastOutLinearInEasing)
    }
}
