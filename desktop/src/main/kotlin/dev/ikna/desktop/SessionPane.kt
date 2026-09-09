package dev.ikna.desktop
import dev.ikna.ui.session.SessionUiState
import dev.ikna.ui.session.IknaSessionTopBar
import dev.ikna.ui.session.IknaTodayProgress
import dev.ikna.ui.session.IknaSessionEmptyState
import dev.ikna.ui.session.IknaSessionUndoBar
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.data.catalog.catalogCardReport
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.domain.session.SessionCard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.delay

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clipToBounds
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
import dev.ikna.domain.session.ReviewSignalTracker
import dev.ikna.domain.session.ReviewSignals
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import dev.ikna.ui.session.ChunkCard
import dev.ikna.ui.session.SwipeableCard
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaProgress
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
    onChanged: () -> Unit,
    onBack: () -> Unit = {}
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
    val clipboard = LocalClipboardManager.current
    var saving by remember { mutableStateOf(false) }
    var answeredHere by remember { mutableStateOf(0) }
    var undoVisible by remember { mutableStateOf(false) }
    var undoFailed by remember { mutableStateOf(false) }
    var undoToken by remember { mutableStateOf(0) }
    var wrongMarked by remember { mutableStateOf(false) }
    var wrongSourceId by remember { mutableStateOf<String?>(null) }
    var reportCopied by remember { mutableStateOf(false) }
    var reportCard by remember { mutableStateOf<SessionCard?>(null) }
    var noMoreExtra by remember { mutableStateOf(false) }
    val swipeFluent = remember(deckId, reload) { settings.swipesDone >= 12 }
    LaunchedEffect(undoToken) {
        if (undoVisible) { delay(6_000L); undoVisible = false }
    }

    LaunchedEffect(deckId, reload) {
        loading = true
        plan = runCatching { container.learningRepository.buildSession(deckId = deckId) }.getOrNull()
        index = 0
        answeredHere = 0
        revealed = false
        shownAt = System.currentTimeMillis()
        loading = false
        runCatching { focus.requestFocus() }
    }

    val cards = plan?.cards.orEmpty()
    val current = cards.getOrNull(index)
    val reviewSignals = remember(deckId, reload, index, current?.card?.key, loading) {
        ReviewSignalTracker()
    }
    val reveal: () -> Unit = {
        reviewSignals.reveal()
        revealed = true
    }

    val advance: () -> Unit = {
        revealed = false
        shownAt = System.currentTimeMillis()
        if (index + 1 < cards.size) index += 1 else reload += 1
        onChanged()
    }

    val gradeWithSignals: (Rating, ReviewSignals) -> Unit = { rating, signals ->
        val card = current
        if (card != null && !loading && !saving && reportCard == null) {
            saving = true
            val took = System.currentTimeMillis() - shownAt
            scope.launch {
                val result = runCatching {
                    container.learningRepository.answer(card, rating, took,
                        now = System.currentTimeMillis(), signals = signals)
                }
                if (result.isSuccess) {
                    if (signals.inputMethod == "swipe") runCatching { container.settings.bumpSwipe() }
                    answeredHere += 1
                    wrongMarked = false
                    undoFailed = false
                    undoVisible = true
                    undoToken += 1
                    note = null
                    advance()
                } else {
                    logLine("answer failed: " + result.exceptionOrNull())
                    note = S.t("set.057")
                }
                saving = false
            }
        }
    }

    val grade: (Rating) -> Unit = { rating ->
        gradeWithSignals(rating, reviewSignals.snapshot(inputMethod = "keyboard"))
    }

    val undo: () -> Unit = {
        if (!loading && !saving) {
            saving = true
            scope.launch {
                val restored = runCatching { container.learningRepository.undoLast() }.getOrNull()
                undoFailed = restored == null
                undoVisible = false
                wrongMarked = false
                if (restored != null) { reload += 1; onChanged() }
                saving = false
            }
        }
    }

    val addMore: () -> Unit = {
        if (!loading && !saving) {
            saving = true
            scope.launch {
                val added = runCatching { container.learningRepository.addExtra(count = 5, deckId = deckId) }.getOrDefault(0)
                noMoreExtra = added == 0
                reload += 1
                onChanged()
                saving = false
            }
        }
    }

    val wrong: () -> Unit = {
        val card = current
        if (card != null && !loading && !saving) {
            saving = true
            scope.launch {
                val result = runCatching { container.learningRepository.markWrong(card) }
                if (result.isSuccess) {
                    wrongMarked = true
                    wrongSourceId = card.sourceId
                    undoVisible = false
                    reload += 1
                    onChanged()
                } else note = S.t("set.057")
                saving = false
            }
        }
    }
    val presentation = SessionUiState(loading = loading, queue = cards, index = index,
        revealed = revealed, remaining = (cards.size - index).coerceAtLeast(0),
        answeredToday = (plan?.answeredToday ?: 0) + answeredHere,
        dailyMinimum = container.learningRepository.dailyMinimum(),
        sessionDone = (plan?.sessionDone ?: 0) + index, sessionTotal = plan?.sessionTotal ?: 0,
        deckTitle = plan?.deckTitle, perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
        reason = plan?.reason ?: dev.ikna.domain.governor.GovernorReason.OK,
        nextDueAt = plan?.nextDueAt, noMoreExtra = noMoreExtra)


    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (loading || saving || reportCard != null || event.type != KeyEventType.KeyDown) {
                    false
                } else when (event.key) {
                    Key.Spacebar, Key.Enter -> {
                        if (current != null && !revealed) { reveal(); true } else false
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
            // Only the header is inset. The card below is the whole pane, edge
            // to edge, exactly as it is the whole screen on the phone.
            .padding(vertical = 0.dp)
    ) {
        IknaSessionTopBar(presentation)
        IknaTodayProgress(state = presentation)

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            } else if (current == null) {
                IknaSessionEmptyState(presentation, settings.animations, onExtra = addMore)
            } else {
                val mode = settings.phoneticsFor(current.chunk.packId)
                val subject = current.chunk.lang == NO_LANG
                // The card IS the pane, the way the card IS the screen on the
                // phone.
                //
                // Two wrong answers came before this one: a 760dp box in the
                // middle of a window, and then a portrait box with an outline.
                // Both of them made the card a small object floating in empty
                // space, which is not what a card is here -- it is the surface
                // you drag, so it takes the whole area it lives in. The two
                // words that appear at the sides sit at the edges of that area,
                // and the area is clipped, so the card slides and leaves inside
                // the pane instead of crossing over the deck list or the window
                // frame.
                val cardWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                // The gesture is a share of the card. The phone's 140 pixels were
                // measured against a screen that IS the card, and thirteen
                // percent of the card's own width is the same drag at any window
                // size, with a floor and a ceiling so a narrow window cannot
                // grade on a twitch and a wide one cannot demand a shove.
                val swipeLine = (cardWidthPx * 0.13f).coerceIn(56f, 220f)
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    SwipeableCard(
                        key = current.card.key + ":" + index,
                        revealed = revealed,
                        animations = settings.animations,
                        haptics = settings.haptics,
                        // The same learned, quiet rails as Android. Pointer distance
                        // remains proportional to the actual desktop pane.
                        railsAtRest = !swipeFluent,
                        onReveal = reveal,
                        onRate = gradeWithSignals,
                        signals = reviewSignals,
                        threshold = swipeLine
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
                            sourceLabel = current.sourceId?.let { S.t("src.001") + "Tatoeba #" + it },
                            onSource = current.sourceId?.let { id -> { tatoebaSentenceUrl(id)?.let(::openInBrowser); Unit } },
                            revealed = revealed,
                            showTapHint = !revealed && index == 0,
                            progress = progress,
                            onTap = reveal,
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

        IknaSessionUndoBar(undoVisible, undoFailed, wrongMarked, wrongSourceId, reportCopied,
            onUndo = undo, onDismiss = { undoVisible = false },
            onOpenSource = { id -> tatoebaSentenceUrl(id)?.let(::openInBrowser) })
        IknaBottomBar {
            IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
            Spacer(Modifier.weight(1f))
            if (current != null && revealed && !saving) {
                IknaTextButton(S.t("sess.044"), onClick = {
                    reportCopied = false
                    if (catalogCardReport(current.chunk) != null) reportCard = current else wrong()
                }, color = palette.muted)
            }
        }
    }
    reportCard?.let { pending ->
        IknaDialog(S.t("src.002"), S.t("src.003"), S.t("src.004"),
            onConfirm = {
                reportCopied = runCatching {
                    val text = catalogCardReport(pending.chunk) ?: return@runCatching false
                    clipboard.setText(AnnotatedString(text)); true
                }.getOrDefault(false)
                reportCard = null
                wrong()
            }, dismissLabel = S.t("src.005"), onDismiss = { reportCard = null })
    }

}

/** What the card is asking, in the phone's own words. */
private fun askLabel(ask: Ask, subject: Boolean): String = when (ask) {
    Ask.RECOGNISE -> if (subject) S.t("sess.046") else S.t("sess.015")
    Ask.GAP -> if (subject) S.t("sess.047") else S.t("sess.016")
    Ask.PRODUCE -> S.t("sess.017")
}
