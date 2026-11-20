package dev.ikna.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.ui.session.ChunkCard
import dev.ikna.ui.session.SwipeableCard
import dev.ikna.ui.text.S
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Real Compose hit testing, clickable, scroll and drag detectors, on a raster
 * Compose scene. No window/X server, learner data, or model-only gesture stub.
 * Touch injection exercises Compose's touch path on JVM, not an Android device.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class CardRevealPointerIntegrationTest(private val mouse: Boolean) {
    companion object {
        @JvmStatic
        @BeforeClass
        fun initializeNativeDatabaseBeforeCompose() {
            // Mirror Main.kt startup and docs/DESKTOP.md's native-runtime order.
            // Skiko-first can corrupt bundled SQLite uniqueness error messages,
            // preventing Room's upsert fallback in later tests in this same JVM.
            BundledSQLiteDriver().open(":memory:").close()
        }

        @JvmStatic
        @Parameterized.Parameters(name = "mouse={0}")
        fun inputs() = listOf(arrayOf(true), arrayOf(false))
    }

    @Test fun frontTapRevealsExactlyOnceWithoutGrading() = checkGesture(Offset.Zero)

    @Test
    fun buttonsRevealInPlaceThenRecordOneAnswer() = runDesktopComposeUiTest(width = 440, height = 560) {
        val revealed = mutableStateOf(false)
        val grades = mutableListOf<Rating>()
        val signals = ReviewSignalTracker()
        val question = "A stable reading prompt"
        setContent {
            MaterialTheme {
                SwipeableCard(
                    key = "button-card",
                    revealed = revealed.value,
                    animations = false,
                    haptics = false,
                    onReveal = { revealed.value = true },
                    onRate = { rating, _ -> grades += rating },
                    signals = signals
                ) {
                    ChunkCard(
                        label = "Synthetic fixture",
                        prompt = question,
                        answer = "A revealed answer",
                        hint = null,
                        revealed = revealed.value,
                        animations = false,
                        onTap = { revealed.value = true },
                        tapEnabled = !revealed.value,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        val questionNode = onNodeWithText(question, useUnmergedTree = true)
        val before = questionNode.getUnclippedBoundsInRoot().top
        onNodeWithText(S.t("card.reveal")).performClick()
        assertEquals(before, questionNode.getUnclippedBoundsInRoot().top)
        runOnIdle { assertTrue(grades.isEmpty()) }
        onNodeWithText(S.t("a11y.009")).performClick()
        runOnIdle { assertEquals(listOf(Rating.GOOD), grades) }
    }

    @Test fun frontSubSlopJitterRevealsExactlyOnceWithoutGrading() =
        checkGesture(Offset(if (mouse) 0.05f else 1f, 0f))

    @Test fun frontShortHorizontalDragRevealsOnReleaseWithoutGrading() =
        checkGesture(Offset(if (mouse) 2f else 20f, 0f))

    @Test fun frontShortLeftDragRevealsOnReleaseWithoutGrading() =
        checkGesture(Offset(if (mouse) -2f else -20f, 0f))

    @Test fun frontLongDragOnlyRevealsAndNextGestureCanGrade() =
        checkGesture(Offset(190f, 0f), gradeNext = true)

    @Test fun batchedFrontDragEventsRevealOnlyOnceAndNeverGrade() =
        checkGesture(Offset(190f, 0f), steps = 3)

    @Test fun delayedRevealStateStillReceivesExactlyOneCallback() =
        checkGesture(Offset(190f, 0f), steps = 3, updateRevealState = false)

    @Test fun replacingCardMidDragCannotRevealOrGradeEitherCardOnRelease() =
        checkGesture(Offset(if (mouse) 2f else 20f, 0f), expectReveal = false, replaceBeforeRelease = true)

    @Test fun verticalMovementPreservesPlatformBehaviorWithoutGrading() =
        // On desktop, mouse-button vertical movement is not a scrolling gesture
        // and the existing clickable reveals on release. Touch scrolls instead.
        checkGesture(Offset(0f, -130f), expectReveal = mouse, longPrompt = true)

    @Test fun cancelledShortHorizontalDragDoesNotRevealOrGrade() =
        checkGesture(Offset(if (mouse) 2f else 20f, 0f), cancel = true, expectReveal = false)

    @Test fun cancelledLongFrontDragMayRevealButNeverGrades() =
        checkGesture(Offset(190f, 0f), cancel = true)

    @Test fun frontReleaseOutsideCardOnlyReveals() =
        checkGesture(Offset(700f, 0f))

    @Test fun secondaryMouseShortDragDoesNotRevealOrGrade() {
        assumeTrue("Mouse-specific input", mouse)
        checkGesture(Offset(2f, 0f), expectReveal = false, secondary = true)
    }

    @Test fun secondaryMouseLongDragDoesNotRevealOrGrade() {
        assumeTrue("Mouse-specific input", mouse)
        checkGesture(Offset(190f, 0f), expectReveal = false, secondary = true)
    }

    @Test fun pressOutsideThenReleaseInsideDoesNotRevealOrGrade() =
        checkGesture(Offset(30f, 0f), expectReveal = false, start = Offset(-10f, 240f))

    @Test fun recognizedShortDragReleasingOutsideRevealsWithoutGrading() =
        checkGesture(Offset(if (mouse) 2f else 20f, 0f), start = Offset(639f, 240f))

    private fun checkGesture(
        delta: Offset,
        cancel: Boolean = false,
        expectReveal: Boolean = true,
        longPrompt: Boolean = false,
        gradeNext: Boolean = false,
        secondary: Boolean = false,
        start: Offset? = null,
        steps: Int = 1,
        updateRevealState: Boolean = true,
        replaceBeforeRelease: Boolean = false
    ) = runDesktopComposeUiTest(width = 800, height = 600) {
        val revealed = mutableStateOf(false)
        val cardKey = mutableStateOf("synthetic-pointer-card")
        val reveals = mutableListOf<String>()
        val grades = mutableListOf<Rating>()
        val signals = ReviewSignalTracker()
        var touchSlop = 0f
        setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
            MaterialTheme {
                // The 1.8.2 desktop injection cancel() is a no-op. A competing
                // consumer at Initial pass instead causes the real drag detector
                // to receive a consumed change and execute onDragCancel.
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().pointerInput(cancel) {
                    awaitPointerEventScope {
                        var moves = 0
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val moved = event.changes.any { it.position != it.previousPosition && it.pressed }
                            if (moved && ++moves >= 2 && cancel) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }) {
                    Box(Modifier.size(640.dp, 480.dp)) {
                        SwipeableCard(
                            key = cardKey.value,
                            revealed = revealed.value,
                            animations = false,
                            haptics = false,
                            onReveal = { input ->
                                reveals += input
                                signals.reveal(input)
                                if (updateRevealState) revealed.value = true
                            },
                            onRate = { rating, _ -> grades += rating },
                            signals = signals
                        ) {
                            ChunkCard(
                                label = "Synthetic fixture",
                                prompt = if (longPrompt) "A reading line.\n".repeat(80) else "A short prompt",
                                answer = "An answer",
                                hint = null,
                                revealed = revealed.value,
                                animations = false,
                                onTap = {
                                    reveals += "tap"
                                    signals.reveal("tap")
                                    if (updateRevealState) revealed.value = true
                                },
                                tapEnabled = !revealed.value,
                                modifier = Modifier.fillMaxSize().testTag("card")
                            )
                        }
                    }
                }
            }
        }
        waitForIdle()
        // The fixture depends on a small drag crossing actual Compose slop,
        // while remaining below the application's extra 16 px peek threshold.
        // Compose 1.8.2 uses touchSlop / 144 for a mouse (upstream detector).
        runOnIdle {
            val slop = if (mouse) touchSlop / 144f else touchSlop
            if (kotlin.math.abs(delta.x) in 2f..20f) {
                assertTrue("fixture must cross Compose slop", kotlin.math.abs(delta.x) > slop)
                assertTrue("fixture must not reach peek travel", kotlin.math.abs(delta.x) - slop < 16f)
            }
        }
        if (replaceBeforeRelease) {
            onNodeWithTag("card").gesture(delta, cancel = false, release = false)
            waitForIdle()
            runOnIdle {
                assertEquals("old card has not revealed before replacement", 0, reveals.size)
                assertEquals("old card has not graded before replacement", emptyList<Rating>(), grades)
                cardKey.value = "replacement-pointer-card"
                revealed.value = false
            }
            waitForIdle()
            onNodeWithTag("card").let { node ->
                if (mouse) node.performMouseInput { release() }
                else node.performTouchInput { up() }
            }
        } else {
            onNodeWithTag("card").gesture(delta, cancel, secondary, start, steps)
        }
        waitForIdle()
        runOnIdle {
            assertEquals("one completed front gesture must never grade", emptyList<Rating>(), grades)
            assertEquals("front reveal count", if (expectReveal) 1 else 0, reveals.size)
            assertEquals("front visibility", expectReveal && updateRevealState, revealed.value)
        }
        // Compose desktop intentionally excludes mouse-button drags from
        // vertical scrolling; the touch path must actually move the viewport.
        if (longPrompt && !mouse) {
            val scroll = onNode(hasScrollAction(), useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            runOnIdle { assertTrue("reading content actually scrolled", scroll.value() > 0f) }
        }
        if (gradeNext) {
            onNodeWithTag("card").gesture(Offset(190f, 0f), cancel = false)
            waitForIdle()
            runOnIdle {
                assertEquals("a fresh answer gesture can grade", listOf(Rating.GOOD), grades)
                assertEquals("answer gesture must not reveal again", 1, reveals.size)
            }
        }
    }

    private fun SemanticsNodeInteraction.gesture(delta: Offset, cancel: Boolean, secondary: Boolean = false, start: Offset? = null, steps: Int = 1, release: Boolean = true) {
        if (mouse) {
            performMouseInput {
                moveTo(start ?: center)
                val button = if (secondary) MouseButton.Secondary else MouseButton.Primary
                press(button)
                if (delta != Offset.Zero) repeat(steps) { moveBy(delta / steps.toFloat(), delayMillis = 100) }
                if (cancel) moveBy(Offset(1f, 0f), delayMillis = 100)
                if (release) release(button)
            }
        } else {
            performTouchInput {
                down(start ?: center)
                if (delta != Offset.Zero) repeat(steps) { moveBy(delta / steps.toFloat(), delayMillis = 100) }
                if (cancel) moveBy(Offset(1f, 0f), delayMillis = 100)
                if (release) up()
            }
        }
    }
}
