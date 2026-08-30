package dev.ikna.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.domain.session.Ask
import dev.ikna.domain.session.SessionPlan
import dev.ikna.ui.session.ChunkCard
import dev.ikna.ui.session.SwipeableCard
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
import kotlinx.coroutines.launch

/**
 * The cards -- the phone's cards, not a desktop retelling of them.
 *
 * The card itself, the drag that grades it, the wash of colour that follows the
 * pointer, the two words at the edges: all of it is the phone's own
 * [SwipeableCard] and [ChunkCard], moved into the shared module rather than
 * reimplemented here. A mouse press and drag is the same gesture as a thumb,
 * so the interaction survived the move unchanged.
 *
 * What the window adds is what a keyboard can offer and a thumb cannot: space
 * to turn a card over, the arrow keys for the two answers, the number keys for
 * all four FSRS grades, and Z to take the last one back.
 */
@Composable
fun SessionPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String?,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<SessionPlan?>(null) }
    var index by remember { mutableStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var shownAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var reload by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(deckId, reload) {
        loading = true
        plan = runCatching { container.learningRepository.buildSession(deckId = deckId) }.getOrNull()
        index = 0
        revealed = false
        shownAt = System.currentTimeMillis()
        loading = false
        runCatching { focus.requestFocus() }
    }

    val cards = plan?.cards.orEmpty()
    val current = cards.getOrNull(index)

    val advance: () -> Unit = {
        revealed = false
        shownAt = System.currentTimeMillis()
        if (index + 1 < cards.size) index += 1 else reload += 1
        onChanged()
    }

    val grade: (Rating) -> Unit = { rating ->
        val card = current
        if (card != null) {
            val took = System.currentTimeMillis() - shownAt
            scope.launch {
                runCatching {
                    container.learningRepository.answer(
                        sessionCard = card,
                        rating = rating,
                        durationMs = took,
                        now = System.currentTimeMillis()
                    )
                }.onFailure { error -> logLine("answer failed: " + error) }
                note = null
                advance()
            }
        }
    }

    val undo: () -> Unit = {
        scope.launch {
            val message = runCatching { container.learningRepository.undoLast() }.getOrNull()
            note = message ?: S.t("sess.011")
            reload += 1
            onChanged()
        }
    }

    val addMore: () -> Unit = {
        scope.launch {
            runCatching { container.learningRepository.addExtra(count = 5, deckId = deckId) }
            reload += 1
            onChanged()
        }
    }

    val wrong: () -> Unit = {
        val card = current
        if (card != null) {
            scope.launch {
                runCatching { container.learningRepository.markWrong(card) }
                note = S.t("sess.045")
                advance()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else when (event.key) {
                    Key.Spacebar, Key.Enter -> {
                        if (current != null && !revealed) { revealed = true; true } else false
                    }
                    Key.DirectionLeft -> if (revealed) { grade(Rating.AGAIN); true } else false
                    Key.DirectionRight -> if (revealed) { grade(Rating.GOOD); true } else false
                    Key.One, Key.NumPad1 -> if (revealed) { grade(Rating.AGAIN); true } else false
                    Key.Two, Key.NumPad2 -> if (revealed) { grade(Rating.HARD); true } else false
                    Key.Three, Key.NumPad3 -> if (revealed) { grade(Rating.GOOD); true } else false
                    Key.Four, Key.NumPad4 -> if (revealed) { grade(Rating.EASY); true } else false
                    Key.Z -> { undo(); true }
                    else -> false
                }
            }
            .padding(horizontal = 40.dp, vertical = 24.dp)
    ) {
        val header = plan?.deckTitle ?: S.t("deck.004")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.weight(1f)
            )
            if (cards.isNotEmpty()) {
                Text(
                    text = (index + 1).toString() + " / " + cards.size,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        IknaProgress(
            fraction = if (cards.isEmpty()) 0f else index.toFloat() / cards.size.toFloat()
        )
        Spacer(Modifier.height(18.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (loading) {
                Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            } else if (current == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val answeredToday = plan?.answeredToday ?: 0
                    Text(
                        text = if (answeredToday > 0) S.t("sess.005") else S.t("sess.006"),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IknaButton(S.t("sess.009"), palette, filled = true) { addMore() }
                        IknaButton(S.t("sess.008"), palette) { reload += 1 }
                    }
                }
            } else {
                val mode = settings.phoneticsFor(current.chunk.packId)
                val subject = current.chunk.lang == NO_LANG
                // The card keeps the proportions it has on a phone rather than
                // stretching to the width of a monitor: a line of text two
                // thousand pixels wide is not a card, it is a paragraph.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    SwipeableCard(
                        key = current.card.key + ":" + index,
                        revealed = revealed,
                        animations = settings.animations,
                        haptics = settings.haptics,
                        // The two words at the edges stay put in a window: a
                        // pointer has no muscle memory to build, and there is
                        // room for them beside the card at any size.
                        railsAtRest = true,
                        onReveal = { revealed = true },
                        onRate = { rating -> grade(rating) }
                    ) { progress ->
                        ChunkCard(
                            label = askLabel(current.ask, subject),
                            prompt = current.prompt,
                            answer = current.answer,
                            promptTarget = current.promptTarget,
                            answerTarget = current.answerTarget,
                            promptTranscription = Phonetics.line(
                                ipa = current.promptIpa,
                                lang = current.chunk.lang,
                                mode = mode
                            ),
                            answerTranscription = Phonetics.line(
                                ipa = current.answerIpa,
                                lang = current.chunk.lang,
                                mode = mode
                            ),
                            // On a subject deck the third field IS the meaning of
                            // the term, so showing it beside a definition with the
                            // term blanked out would simply print the answer.
                            hint = if (current.ask == Ask.GAP && !subject) {
                                current.meaning
                            } else {
                                null
                            },
                            revealed = revealed,
                            showTapHint = !revealed && index == 0,
                            progress = progress,
                            onTap = { revealed = true },
                            tapEnabled = !revealed,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        val message = note
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }

        if (current != null) {
            Spacer(Modifier.height(12.dp))
            IknaRule(color = palette.line)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!revealed) {
                    IknaButton(S.t("card.002"), palette, filled = true) { revealed = true }
                } else {
                    IknaButton(S.t("card.003"), palette) { grade(Rating.AGAIN) }
                    IknaButton(S.t("card.004"), palette, filled = true) { grade(Rating.GOOD) }
                    IknaButton(S.t("sess.044"), palette) { wrong() }
                }
                Box(Modifier.weight(1f))
                IknaButton(S.t("sess.013"), palette) { undo() }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = S.t("pc.014") + "   " + S.t("pc.002"),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }
    }
}

/** What the card is asking, in the phone's own words. */
private fun askLabel(ask: Ask, subject: Boolean): String = when (ask) {
    Ask.RECOGNISE -> if (subject) S.t("sess.046") else S.t("sess.015")
    Ask.GAP -> if (subject) S.t("sess.047") else S.t("sess.016")
    Ask.PRODUCE -> S.t("sess.017")
}
