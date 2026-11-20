package dev.ikna.ui.session

import dev.ikna.ui.text.S

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.repo.DeckSummary
import dev.ikna.domain.fsrs.Rating
import dev.ikna.ui.decks.IknaDeckMark
import dev.ikna.domain.grading.INPUT_ACCESSIBILITY
import dev.ikna.domain.grading.INPUT_BUTTON
import dev.ikna.domain.grading.INPUT_KEYBOARD
import dev.ikna.domain.grading.INPUT_SWIPE
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.domain.session.ReviewSignals
import dev.ikna.domain.session.TimingDiscardReason
import dev.ikna.ui.theme.IknaScrollableColumn
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.LocalIknaMotionEnabled
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaTileShape
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val EXIT_X = 1400f

/** A real review command from a non-pointer input, never fake pointer telemetry. */
data class ProgrammaticSwipe(val token: Int, val rating: Rating)

data class CardShortcuts(val reveal: String?, val miss: String?, val know: String?)

private fun withShortcut(label: String, shortcut: String?): String =
    if (shortcut.isNullOrBlank()) label else "$label  ($shortcut)"

/** How far the card leans as it travels. Larger divisor, calmer rotation. */
private const val ROTATION_DIVISOR = 64f

private val CARD_MARGIN = 16.dp

/**
 * The reading card follows the pointer. A front-side drag can reveal the answer,
 * while a rating requires a new gesture that began with the answer visible.
 */
@Composable
fun SwipeableCard(
    key: String,
    revealed: Boolean,
    animations: Boolean,
    haptics: Boolean,
    hasNextCard: Boolean = false,
    saving: Boolean = false,
    shortcuts: CardShortcuts? = null,
    onReveal: (String) -> Unit,
    onRate: (Rating, ReviewSignals) -> Unit,
    signals: ReviewSignalTracker,
    programmaticSwipe: ProgrammaticSwipe? = null,
    onProgrammaticSwipeHandled: (Int) -> Unit = {},
    /**
     * How far the card has to travel to become an answer, in pixels.
     *
     * Left at the default on a phone, where the card is the screen. A window
     * passes a share of the card own width, so the gesture stays in the same
     * proportion to the card whatever size the window is.
     */
    threshold: Float = SWIPE_THRESHOLD,
    content: @Composable () -> Unit
) {
    val offsetX = remember(key) { Animatable(0f) }
    // Where the pointer has taken the card, held in a plain state.
    //
    // This used to be a coroutine per pointer event -- scope.launch { snapTo }
    // on every single move. A thumb can just about afford that; a mouse cannot.
    // A mouse reports hundreds of moves a second, and each one allocated a
    // coroutine and queued it on the UI thread before a single pixel moved,
    // which is exactly what a card that will not keep up with the cursor feels
    // like. The card now follows the pointer synchronously, and the Animatable
    // is left to the two things that really are animations: the spring back and
    // the throw off screen.
    val drag = remember(key) { mutableStateOf(0f) }
    val flying = remember(key) { mutableStateOf(false) }
    val usesAnimatedOffset = remember(key) { mutableStateOf(false) }
    val shift: () -> Float = { if (usesAnimatedOffset.value) offsetX.value else drag.value }
    // 0 while the card is still arriving, 1 once it has taken its place.
    val arrival = remember(key) { Animatable(if (animations) 0f else 1f) }
    val armed = remember(key) { mutableStateOf<Rating?>(null) }
    // Was the back of the card visible when this gesture began? Read once, at
    // the start, so a pull that reveals the answer cannot also grade it.
    val gradable = remember(key) { mutableStateOf(false) }
    val throwing = remember(key) { mutableStateOf<Rating?>(null) }
    val currentKey = rememberUpdatedState(key)
    val savingNow = rememberUpdatedState(saving)
    val tracker = remember(key) { VelocityTracker() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // The gesture detector outlives recomposition, so everything it reads has to
    // be read through a handle that stays current. Capturing the values directly
    // would freeze the card in the state it had when the drag loop started.
    val revealedNow = rememberUpdatedState(revealed)
    val revealNow = rememberUpdatedState(onReveal)
    val rateNow = rememberUpdatedState(onRate)
    val programmaticHandledNow = rememberUpdatedState(onProgrammaticSwipeHandled)
    val windowInfo = LocalWindowInfo.current

    // Observation only: neither focus nor timing is allowed to block an answer.
    LaunchedEffect(signals, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (!focused) signals.interrupt(TimingDiscardReason.FOCUS_LOST)
        }
    }
    DisposableEffect(signals) {
        onDispose { signals.interrupt(TimingDiscardReason.PRESENTATION_INTERRUPTED) }
    }

    LaunchedEffect(key, animations) {
        if (animations) arrival.animateTo(1f, tween(230, easing = LinearOutSlowInEasing)) else arrival.snapTo(1f)
    }

    val line = if (threshold > 0f) threshold else SWIPE_THRESHOLD
    val progress: () -> Float = {
        when (throwing.value) {
            Rating.AGAIN -> -1f
            Rating.GOOD -> 1f
            else -> if (gradable.value) (shift() / line).coerceIn(-1f, 1f) else 0f
        }
    }
    val ready: () -> Boolean = { armed.value != null || throwing.value != null }

    suspend fun completeDirectGrade(rating: Rating, observation: ReviewSignals) {
        throwing.value = rating
        try {
            if (animations) {
                offsetX.snapTo(drag.value)
                usesAnimatedOffset.value = true
                throwOut(offsetX, rating, Velocity.Zero)
            }
            if (currentKey.value == key) {
                rateNow.value(rating, observation)
                if (animations) withFrameNanos { }
            }
        } finally {
            drag.value = 0f
            usesAnimatedOffset.value = false
            throwing.value = null
            flying.value = false
        }
    }

    // Buttons and keys use the same short transition and keep their input
    // evidence distinct from the pointer's distance and velocity.
    LaunchedEffect(key, programmaticSwipe?.token) {
        val command = programmaticSwipe ?: return@LaunchedEffect
        if (!revealedNow.value) {
            programmaticHandledNow.value(command.token)
            return@LaunchedEffect
        }
        if (flying.value) snapshotFlow { flying.value }.first { active -> !active }
        if (revealedNow.value && !savingNow.value && currentKey.value == key) {
            flying.value = true
            signals.keyboardStarted()
            completeDirectGrade(command.rating, signals.snapshot(inputMethod = INPUT_KEYBOARD))
        }
        programmaticHandledNow.value(command.token)
    }

    fun rateFromButton(rating: Rating) {
        if (!revealedNow.value || flying.value || savingNow.value) return
        flying.value = true
        signals.answerStarted(INPUT_BUTTON)
        val observation = signals.snapshot(inputMethod = INPUT_BUTTON)
        scope.launch {
            completeDirectGrade(rating, observation)
        }
    }

    val revealAction = S.t("card.reveal")
    val missAction = S.t("a11y.008")
    val keepAction = S.t("a11y.009")

    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { signals.shown() }
                .semantics {
                    // TalkBack follows the same two-step contract as a finger. The
                    // old semantics exposed both grades on the front, so a screen-
                    // reader action could record an answer that had never been
                    // revealed even though touch input deliberately forbids that.
                    customActions = if (revealed) {
                        listOf(
                            CustomAccessibilityAction(keepAction) {
                                val canRate = revealedNow.value && !flying.value && !savingNow.value
                                if (canRate) {
                                    signals.answerStarted(INPUT_ACCESSIBILITY)
                                    rateNow.value(Rating.GOOD, signals.snapshot())
                                }
                                canRate
                            },
                            CustomAccessibilityAction(missAction) {
                                val canRate = revealedNow.value && !flying.value && !savingNow.value
                                if (canRate) {
                                    signals.answerStarted(INPUT_ACCESSIBILITY)
                                    rateNow.value(Rating.AGAIN, signals.snapshot())
                                }
                                canRate
                            }
                        )
                    } else {
                        listOf(
                            CustomAccessibilityAction(revealAction) {
                                val canReveal = !revealedNow.value && !savingNow.value
                                if (canReveal) revealNow.value(INPUT_ACCESSIBILITY)
                                canReveal
                            }
                        )
                    }
                }
                // On the whole screen rather than on the card: the card moves, and a
                // touch area that moves with it stops accepting the second half of a
                // long drag.
                // Vertical drags belong to the reading viewport, never a rating.
                .pointerInput(key, signals) {
                    var revealedByDrag = false
                    var acceptedDrag = false
                    detectHorizontalDragGestures(
                        onDragStart = {
                            acceptedDrag = !flying.value && !savingNow.value
                            if (!acceptedDrag || flying.value || savingNow.value) return@detectHorizontalDragGestures
                            tracker.resetTracking()
                            armed.value = null
                            revealedByDrag = false
                            gradable.value = revealedNow.value
                            if (gradable.value) signals.dragStarted()
                        },
                        onHorizontalDrag = { change, delta ->
                            if (!acceptedDrag || flying.value || savingNow.value) return@detectHorizontalDragGestures
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = drag.value + delta
                            drag.value = next
                            if (gradable.value) {
                                // One tick when the gesture becomes an answer, one when
                                // it stops being one. Never a stream of them.
                                val candidate = armedRating(next, 0f, line)
                                if (candidate != armed.value) {
                                    if (candidate != null && haptics) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    armed.value = candidate
                                }
                            } else if (!revealedByDrag && !revealedNow.value && abs(next) >= PEEK_TRAVEL) {
                                revealedByDrag = true
                                revealNow.value(INPUT_SWIPE)
                            }
                        },
                        onDragCancel = {
                            if (!acceptedDrag || flying.value || savingNow.value) return@detectHorizontalDragGestures
                            armed.value = null
                            flying.value = true
                            scope.launch {
                                offsetX.snapTo(drag.value)
                                usesAnimatedOffset.value = true
                                settle(offsetX, animations)
                                drag.value = 0f
                                usesAnimatedOffset.value = false
                                flying.value = false
                            }
                        },
                        onDragEnd = {
                            // Compose has already consumed this horizontal gesture,
                            // cancelling the inner click. A short pull below the peek
                            // distance must still reveal on release, not do nothing.
                            // Keep the start-of-gesture grade gate: this release can
                            // never also answer the newly revealed card. Cancellation
                            // deliberately has no corresponding fallback.
                            if (!acceptedDrag || flying.value || savingNow.value) return@detectHorizontalDragGestures
                            if (!gradable.value && !revealedByDrag && !revealedNow.value) {
                                revealedByDrag = true
                                revealNow.value(INPUT_SWIPE)
                            }
                            val velocity = tracker.calculateVelocity()
                            val rating =
                                if (gradable.value) decideRating(drag.value, 0f, velocity.x, line) else null
                            armed.value = null
                            flying.value = true
                            if (rating == null) {
                                scope.launch {
                                    offsetX.snapTo(drag.value)
                                    usesAnimatedOffset.value = true
                                    settle(offsetX, animations, velocity)
                                    drag.value = 0f
                                    usesAnimatedOffset.value = false
                                    flying.value = false
                                }
                            } else {
                                // Freeze before animation: its duration and any later
                                // queued disk work are not retrieval latency.
                                val observation = signals.snapshot(velocity.x)
                                throwing.value = rating
                                scope.launch {
                                    offsetX.snapTo(drag.value)
                                    usesAnimatedOffset.value = true
                                    if (animations) throwOut(offsetX, rating, velocity)
                                    if (currentKey.value == key) rateNow.value(rating, observation)
                                    throwing.value = null
                                    offsetX.snapTo(0f)
                                    drag.value = 0f
                                    usesAnimatedOffset.value = false
                                    flying.value = false
                                }
                            }
                        }
                    )
                }
        ) {
            SwipeGlow(progress, ready, animations)
            if (hasNextCard && animations && (throwing.value != null || saving)) {
                IknaCardBacking(
                    Modifier.matchParentSize().graphicsLayer {
                        val leaving = when {
                            saving -> 1f
                            else -> (abs(shift()) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        }
                        scaleX = 0.96f + 0.04f * leaving
                        scaleY = scaleX
                        translationY = (1f - leaving) * 12.dp.toPx()
                    }
                )
            }
            // The travelling layer.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val landed = arrival.value.coerceIn(0f, 1f)
                        val travelled = shift()
                        translationX = travelled
                        val lift = abs(progress())
                        translationY = if (animations) ((1f - landed) * 5f - 4f * lift).dp.toPx() else 0f
                        rotationZ = if (animations) (travelled / ROTATION_DIVISOR).coerceIn(-6f, 6f) else 0f
                        alpha = if (saving && animations) 0f else landed
                    }
            ) {
                content()
            }

            if (revealed) SwipeIntent(progress, ready)
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = CARD_MARGIN, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (revealed) {
                IknaWideButton(
                    label = withShortcut(missAction, shortcuts?.miss),
                    onClick = { rateFromButton(Rating.AGAIN) },
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    enabled = !flying.value && !saving
                )
                IknaWideButton(
                    label = withShortcut(keepAction, shortcuts?.know),
                    onClick = { rateFromButton(Rating.GOOD) },
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    enabled = !flying.value && !saving
                )
            } else {
                IknaWideButton(
                    label = withShortcut(revealAction, shortcuts?.reveal),
                    onClick = { if (!revealedNow.value && !flying.value && !savingNow.value) revealNow.value(INPUT_BUTTON) },
                    filled = true,
                    height = 48.dp,
                    enabled = !flying.value && !saving
                )
            }
        }
    }
}

/** Back to rest, carrying whatever speed the finger left behind. */
private suspend fun settle(
    x: Animatable<Float, AnimationVector1D>,
    animations: Boolean,
    velocity: Velocity = Velocity.Zero
) {
    if (!animations) {
        x.snapTo(0f)
        return
    }
    x.animateTo(0f, Motion.settle, initialVelocity = velocity.x)
}

/** Off the screen in the direction it was thrown, at the speed it was thrown. */
private suspend fun throwOut(
    x: Animatable<Float, AnimationVector1D>,
    rating: Rating,
    velocity: Velocity
) {
    val target = if (rating == Rating.AGAIN) -EXIT_X else EXIT_X
    x.animateTo(target, Motion.thrown(abs(velocity.x)), initialVelocity = velocity.x)
}

/** A reading surface with a stable question and an answer that opens below it. */
@Composable
fun ChunkCard(
    label: String,
    language: String? = null,
    deck: DeckSummary? = null,
    look: DeckLook = DeckLook(),
    prompt: String,
    answer: String,
    /** Where the phrase sits inside [prompt], when the prompt is a sentence. */
    promptTarget: IntRange? = null,
    /** The same, for the sentence shown as the answer of a production card. */
    answerTarget: IntRange? = null,
    /**
     * How the prompt sounds, already rendered into whatever notation the deck
     * is set to. Null draws nothing and reserves no height, which is not the
     * same as an empty string: a blank line under a phrase reads as a layout
     * fault, and most decks in the world have no transcription at all.
     */
    promptTranscription: String? = null,
    /** The same under the answer. */
    answerTranscription: String? = null,
    hint: String?,
    /** Public source shown only after the answer is visible. */
    sourceLabel: String? = null,
    onSource: (() -> Unit)? = null,
    revealed: Boolean,
    animations: Boolean = LocalIknaMotionEnabled.current,
    onTap: () -> Unit,
    tapEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    val surface = lerp(palette.background, palette.surfaceVariant, 0.22f)
    val answerAlpha = animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = if (animations) tween(190, easing = LinearOutSlowInEasing) else snap(),
        label = "card-answer"
    )

    Box(
        modifier = modifier
            .padding(horizontal = CARD_MARGIN, vertical = Space.sm)
            .clip(shape)
            .background(surface)
            .border(1.dp, palette.outlineVariant.copy(alpha = 0.65f), shape)
            .clickable(
                enabled = tapEnabled,
                onClickLabel = S.t("a11y.016"),
                onClick = onTap
            )
    ) {
        IknaScrollableColumn(
            modifier = Modifier
                .fillMaxHeight()
                // The reading measure: the phrase composes as a centred column,
                // while the silent field and the throwing gesture keep the whole
                // pane. On the phone the pane is the measure, so nothing moves.
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .padding(horizontal = Space.lg)
                .padding(vertical = Space.lg),
            scrollKey = prompt
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (deck != null) {
                    IknaDeckMark(deck, owes = false, look = look, compact = true)
                } else if (language != null) {
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.primary,
                        modifier = Modifier
                            .background(palette.primary.copy(alpha = 0.10f), shape)
                            .padding(horizontal = Space.sm, vertical = Space.xs)
                    )
                }
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Space.xl))

            Text(
                text = marked(prompt, promptTarget, MaterialTheme.colorScheme.primary),
                style = promptStyle(prompt),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Pronunciation sits directly under the words it belongs to, in the
            // muted colour and at body size. Deliberately not styled like the
            // phrase: it is a note about the line above, and a second line in
            // the same weight would read as a second thing to learn.
            if (!promptTranscription.isNullOrBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = promptTranscription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (revealed) {
                Spacer(Modifier.height(Space.lg))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = answerAlpha.value
                            translationY = (1f - answerAlpha.value) * 3.dp.toPx()
                        }
                        .background(palette.primary.copy(alpha = 0.07f), shape)
                        .padding(Space.md)
                ) {
                    Text(
                        text = marked(answer, answerTarget, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!answerTranscription.isNullOrBlank()) {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            text = answerTranscription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!hint.isNullOrBlank()) {
                        Spacer(Modifier.height(Space.md))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!sourceLabel.isNullOrBlank() && onSource != null) {
                        Spacer(Modifier.height(Space.md))
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable(onClickLabel = S.t("a11y.017"), onClick = onSource)
                        )
                    }
                }
            }
        }
    }
}

/** Face-down preview while the next prompt waits for the current answer to save. */
@Composable
fun IknaCardBacking(modifier: Modifier = Modifier) {
    val shape = iknaTileShape()
    val palette = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(horizontal = CARD_MARGIN, vertical = Space.sm)
            .background(lerp(palette.background, palette.surfaceVariant, 0.22f), shape)
            .border(1.dp, palette.outlineVariant, shape)
    )
}

@Composable
private fun BoxScope.SwipeGlow(progress: () -> Float, ready: () -> Boolean, animations: Boolean) {
    val emphasis = animateFloatAsState(
        targetValue = if (ready()) 1f else 0f,
        animationSpec = if (animations) tween(90) else snap(),
        label = "swipe-glow-threshold"
    )
    val known = MaterialTheme.colorScheme.primary
    val unknown = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier.matchParentSize().drawBehind {
            val direction = progress()
            val distance = abs(direction)
            if (distance == 0f || size.width <= 0f || size.height <= 0f) return@drawBehind
            val color = if (direction > 0f) known else unknown
            val reach = distance * (2f - distance)
            val intensity = reach * (0.34f + 0.12f * emphasis.value)
            val toward = if (direction > 0f) size.width else 0f
            drawRect(
                brush = Brush.linearGradient(
                    0f to color.copy(alpha = 0f),
                    0.47f to color.copy(alpha = intensity * 0.18f),
                    0.78f to color.copy(alpha = intensity * 0.62f),
                    1f to color.copy(alpha = intensity),
                    start = Offset(size.width - toward, size.height * 0.34f),
                    end = Offset(toward, size.height)
                )
            )
        }
    )
}

@Composable
private fun BoxScope.SwipeIntent(progress: () -> Float, ready: () -> Boolean) {
    val palette = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    for (known in listOf(false, true)) {
        val color = if (known) palette.primary else palette.tertiary
        Text(
            text = S.t(if (known) "card.004" else "card.003"),
            style = MaterialTheme.typography.labelLarge,
            color = palette.onSurface,
            modifier = Modifier
                .align(if (known) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = Space.lg, vertical = Space.md)
                .clearAndSetSemantics { }
                .graphicsLayer {
                    val toward = if (known) progress() else -progress()
                    val distance = toward.coerceIn(0f, 1f)
                    alpha = distance * (if (ready()) 1f else 0.86f)
                }
                .background(lerp(palette.surface, color, 0.20f), shape)
                .padding(horizontal = Space.md, vertical = Space.xs)
        )
    }
}

/**
 * Big by default, smaller only when the chunk is genuinely long.
 *
 * Three steps and no auto-fitting: a size that changes by one point per card is
 * a size that never looks deliberate.
 */
@Composable
private fun promptStyle(prompt: String): TextStyle = when {
    prompt.length <= 20 -> MaterialTheme.typography.displayMedium
    prompt.length <= 42 -> MaterialTheme.typography.displaySmall
    else -> MaterialTheme.typography.headlineMedium
}

/**
 * The phrase being learned, marked inside the sentence around it.
 *
 * Underlined and set in the accent rather than emboldened: bold changes the
 * shape of a word, which is the shape the reader is trying to remember, and a
 * filled highlight puts a block of colour in the middle of a line that has to
 * stay readable. A rule under the words and the palette's own colour say "this
 * part" without altering the letters.
 *
 * The range is clamped here as well as where it is computed. This function is
 * handed a string and a pair of numbers from the database, and an out-of-range
 * span throws inside the text layer -- in the middle of a session, on a card
 * that would then be unopenable forever.
 */
private fun marked(text: String, target: IntRange?, color: Color): AnnotatedString {
    if (target == null) return AnnotatedString(text)
    val start = target.first.coerceIn(0, text.length)
    val end = (target.last + 1).coerceIn(start, text.length)
    if (end <= start) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(color = color, textDecoration = TextDecoration.Underline),
            start,
            end
        )
    }
}
