package dev.ikna.ui.session
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.domain.governor.GovernorReason
import dev.ikna.ui.text.S
import dev.ikna.ui.text.quantityWord
import dev.ikna.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
private val BAR_HEIGHT = 44.dp
private val UNDO_HEIGHT = 52.dp
private val EDGE = 20.dp
private var lastCelebratedAnswersToday: Int = 0

@Composable
fun IknaSessionTopBar(state: SessionUiState, onFinishOptional: (() -> Unit)? = null) {
    val minimumJustMet = state.minimumMet && state.goalAnswersToday == state.dailyMinimum
    val text = when {
        state.optionalHardReview -> S.t("sess.hard.active")
        state.index == 0 && !state.revealed && state.remaining > 0 -> startEstimate(state)
        minimumJustMet -> S.t("sess.003")
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = iknaNumberStyle(MaterialTheme.typography.labelMedium),
            color = if (minimumJustMet) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        if (state.optionalHardReview && onFinishOptional != null) {
            IknaTextButton(label = S.t("sess.hard.finish"), onClick = onFinishOptional)
        } else if (state.deckTitle != null) {
            Text(
                text = state.deckTitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }
}

/** Names the short-term bar and states its finite daily scope. */
@Composable
fun IknaTodayProgress(
    state: SessionUiState,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val total = state.sessionTotal.coerceAtLeast(0)
    val done = state.sessionDone.coerceIn(0, total)
    // The word "today" lives on the deck screen that opened this session; here
    // the counter alone is the fact, and whatever trails it sits beside it.
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$done / $total",
                style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            trailing?.invoke(this)
        }
        IknaProgress(fraction = state.progress)
    }
}

@Composable
fun IknaSessionEmptyState(
    state: SessionUiState,
    animations: Boolean,
    onExtra: () -> Unit,
    checkExtra: suspend (Long) -> Boolean,
    onOptionalHard: (() -> Unit)? = null
) {
    val check by rememberUpdatedState(checkExtra)
    val availability by produceState<Pair<Instant, Boolean>?>(null, state.deckTitle,
        state.answeredToday, state.noMoreExtra, state.optionalHardReview) {
        value = null
        while (true) {
            val now = Instant.now()
            val extra = try {
                check(now.toEpochMilli())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            value = now to extra
            delay(60_000)
        }
    }
    val result = availability
    IknaScrollableColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EDGE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The app's own mark, drawn once when the day closes. This was a Lottie
        // file: an animation runtime plus a JSON asset, shipped to play one shape
        // once a day. The shape is now the launcher icon itself, in the user's own
        // accent colour, so the object that ends a session is the object that
        // started it.
        if (state.answeredToday > 0) {
            val sound = LocalIknaSoundFeedback.current
            val appear = remember { Animatable(if (animations) 0f else 1f) }
            LaunchedEffect(state.answeredToday) {
                if (state.answeredToday != lastCelebratedAnswersToday) {
                    lastCelebratedAnswersToday = state.answeredToday
                    sound.play(IknaSoundCue.COMPLETE)
                }
            }
            LaunchedEffect(animations) {
                if (animations) appear.animateTo(1f, Motion.reveal) else appear.snapTo(1f)
            }
            IknaSpark(
                color = MaterialTheme.colorScheme.primary,
                size = 120.dp,
                progress = appear.value
            )
            Spacer(Modifier.height(Space.lg))
        }

        Text(
            text = when {
                state.optionalHardReview -> S.t("sess.hard.done")
                state.reason == GovernorReason.PACK_EXHAUSTED -> S.t("sess.004")
                state.answeredToday > 0 -> S.t("sess.005")
                else -> S.t("sess.006")
            },
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (result != null && !result.second && !state.optionalHardReview) {
                S.t(sessionFinishMessageKey(state, result.first))
            } else emptyExplanation(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        nextDueLabel(state.nextDueAt)?.let { label ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        if (state.optionalHardEmpty) {
            Text(S.t("sess.hard.empty"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.md))
        }
        if (state.optionalHardAvailable && onOptionalHard != null) {
            IknaTextButton(label = S.t("sess.hard.start"), onClick = onOptionalHard)
            Text(S.t("sess.hard.scope"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.md))
        }

        if (result?.second == true) {
            IknaTextButton(label = S.t("sess.009"), onClick = onExtra)
            Spacer(Modifier.height(Space.md))
            Text(
                text = S.t("sess.010"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private enum class UndoCapsuleMode { FAILED, WRONG, UNDO }

@Composable
fun IknaSessionUndoBar(
    visible: Boolean,
    failed: Boolean,
    wrong: Boolean,
    wrongSourceId: String?,
    reportCopied: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    val activeMode = when {
        failed -> UndoCapsuleMode.FAILED
        wrong -> UndoCapsuleMode.WRONG
        visible -> UndoCapsuleMode.UNDO
        else -> null
    }
    var renderedMode by remember { mutableStateOf(activeMode ?: UndoCapsuleMode.UNDO) }
    if (activeMode != null) renderedMode = activeMode

    val motionEnabled = LocalIknaMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    val rounded = LocalIknaAppearanceVariant.current == IknaAppearanceVariant.ROUNDED
    val capsuleShape = RoundedCornerShape(if (rounded) 14.dp else 0.dp)
    val actionShape = RoundedCornerShape(if (rounded) 9.dp else 0.dp)
    val surfaceFill = lerp(colors.surface, colors.onSurface, 0.06f)
    val borderColor = colors.outline.copy(alpha = 0.42f)
    val sound = LocalIknaSoundFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UNDO_HEIGHT)
            .padding(horizontal = EDGE),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = activeMode != null,
            enter = if (motionEnabled) {
                fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.96f)
            } else {
                EnterTransition.None
            },
            exit = if (motionEnabled) fadeOut(tween(110)) else ExitTransition.None
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .heightIn(min = 40.dp)
                    .clip(capsuleShape)
                    .background(surfaceFill, capsuleShape)
                    .border(1.dp, borderColor, capsuleShape)
                    .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (renderedMode) {
                    UndoCapsuleMode.FAILED -> {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(actionShape)
                                .background(colors.error.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IknaGlyphIcon(
                                glyph = IknaGlyph.CLOSE,
                                color = colors.error,
                                size = 11.dp
                            )
                        }
                        Text(
                            text = S.t("sess.011"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }

                    // Said once, without an undo beside it: the card is gone on
                    // purpose, and it can be brought back in settings if that was a
                    // mistake, which is the right amount of friction for a decision
                    // about the deck rather than about an answer.
                    UndoCapsuleMode.WRONG -> {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(actionShape)
                                .background(colors.outline.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IknaGlyphIcon(
                                glyph = IknaGlyph.CHECK,
                                color = colors.onSurfaceVariant,
                                size = 12.dp
                            )
                        }
                        Text(
                            text = if (reportCopied) S.t("src.006") else S.t("sess.045"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (wrongSourceId != null) {
                            IknaTextButton(
                                label = S.t("src.007"),
                                onClick = { onOpenSource(wrongSourceId) }
                            )
                        }
                    }

                    UndoCapsuleMode.UNDO -> {
                        val undoInteraction = remember { MutableInteractionSource() }
                        val undoHovered by undoInteraction.collectIsHoveredAsState()
                        val undoPressed by undoInteraction.collectIsPressedAsState()
                        val undoScale = iknaPressScale(undoPressed, motionEnabled, pressedScale = 0.96f)

                        val dismissInteraction = remember { MutableInteractionSource() }
                        val dismissHovered by dismissInteraction.collectIsHoveredAsState()
                        val dismissPressed by dismissInteraction.collectIsPressedAsState()
                        val dismissScale = iknaPressScale(dismissPressed, motionEnabled, pressedScale = 0.94f)

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(actionShape)
                                .background(colors.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            IknaGlyphIcon(
                                glyph = IknaGlyph.CHECK,
                                color = colors.primary,
                                size = 12.dp
                            )
                        }
                        Text(
                            text = S.t("sess.012"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(colors.outline.copy(alpha = 0.35f))
                        )
                        Row(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = undoScale
                                    scaleY = undoScale
                                }
                                .clip(actionShape)
                                .background(
                                    colors.primary.copy(
                                        alpha = when {
                                            undoPressed -> 0.24f
                                            undoHovered -> 0.18f
                                            else -> 0.11f
                                        }
                                    ),
                                    actionShape
                                )
                                .hoverable(undoInteraction)
                                .clickable(
                                    interactionSource = undoInteraction,
                                    indication = null,
                                    role = Role.Button
                                ) {
                                    sound.play(IknaSoundCue.TAP)
                                    onUndo()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IknaGlyphIcon(
                                glyph = IknaGlyph.UNDO,
                                color = colors.primary,
                                size = 13.dp
                            )
                            Text(
                                text = S.t("sess.013"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .graphicsLayer {
                                    scaleX = dismissScale
                                    scaleY = dismissScale
                                }
                                .clip(actionShape)
                                .background(
                                    if (dismissHovered || dismissPressed) colors.onSurface.copy(alpha = 0.08f)
                                    else colors.surface.copy(alpha = 0f),
                                    actionShape
                                )
                                .hoverable(dismissInteraction)
                                .clickable(
                                    interactionSource = dismissInteraction,
                                    indication = null,
                                    role = Role.Button
                                ) {
                                    sound.play(IknaSoundCue.TAP)
                                    onDismiss()
                                }
                                .semantics { contentDescription = S.t("sess.014") },
                            contentAlignment = Alignment.Center
                        ) {
                            IknaGlyphIcon(
                                glyph = IknaGlyph.CLOSE,
                                color = if (dismissHovered) colors.onSurface else colors.onSurfaceVariant,
                                size = 12.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun emptyExplanation(state: SessionUiState): String = when {
    state.optionalHardReview -> S.t("sess.hard.done_scope")
    state.reason == GovernorReason.PACK_EXHAUSTED -> reasonText(state.reason)
    state.answeredToday > 0 -> S.t("sess.018")
    else -> reasonText(state.reason)
}

internal fun reasonText(reason: GovernorReason): String = when (reason) {
    GovernorReason.FIRST_RUN -> S.t("sess.019")
    GovernorReason.OK -> S.t("sess.020")
    GovernorReason.NO_HEADROOM -> S.t("sess.021")
    GovernorReason.BACKLOG_LIMIT -> S.t("sess.022")
    GovernorReason.POST_SKIP_WARMUP -> S.t("sess.023")
    GovernorReason.LOW_ACTIVITY -> S.t("sess.024")
    GovernorReason.LATE_NIGHT -> S.t("sess.025")
    GovernorReason.LOW_ACCURACY -> S.t("sess.026")
    GovernorReason.RETURN_MODE -> S.t("sess.027")
    GovernorReason.SAFETY_VALVE -> S.t("sess.028")
    GovernorReason.OVERHEATED -> S.t("sess.043")
    GovernorReason.PACK_EXHAUSTED ->
        S.t("sess.029")
}

private fun nextDueLabel(nextDueAt: Long?): String? {
    if (nextDueAt == null) return null
    val now = System.currentTimeMillis()
    if (nextDueAt <= now) return null
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(nextDueAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = then.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (val days = ChronoUnit.DAYS.between(today, then.toLocalDate())) {
        0L -> S.t("sess.030") + time
        1L -> S.t("sess.031") + time
        else -> S.t("sess.032") + days + " " + dayWord(days)
    }
}

private fun dayWord(days: Long): String {
    return quantityWord(days, "sess.033", "sess.034", "sess.035", "sess.036")
}

// ---- how long this will take ----------------------------------------------

/**
 * "12 КАРТОЧЕК · ~3 МИН", shown once, before the first answer.
 *
 * A number of cards is not a unit anyone can plan with, and "some cards" is
 * exactly the shape of task that gets postponed: the cost is unknown, so the
 * brain prices it as expensive. Minutes are a unit you can decide about while
 * the kettle boils. The figure comes from the user's own recent answers rather
 * than a constant, and while there is not enough history to measure it, nothing
 * is shown — an estimate that turns out to be a lie costs more trust than it
 * buys.
 *
 * It disappears the moment the first card is opened, because after the decision
 * to start has been made the same figure is just a countdown.
 */
private fun startEstimate(state: SessionUiState): String {
    val count = state.remaining
    val base = (count.toString() + " " + cardWord(count)).uppercase()
    val perCard = state.perCardMs ?: return base
    val totalMs = count * perCard
    if (totalMs < 45_000L) return base + S.t("sess.037")
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return base + " · ~" + minutes + S.t("sess.038")
}

private fun cardWord(count: Int): String {
    return quantityWord(count.toLong(), "sess.039", "sess.040", "sess.041", "sess.042")
}

/** A changed/missing plan is neither completion nor an I/O failure. */
@Composable
fun IknaSessionChanged(onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = EDGE)) {
        Text(S.t("sess.changed.message"), style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.md))
        IknaTextButton(label = S.t("sess.changed.continue"), onClick = onContinue)
    }
}
