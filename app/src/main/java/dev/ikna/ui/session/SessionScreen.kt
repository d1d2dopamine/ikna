package dev.ikna.ui.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.ikna.domain.session.Ask
import dev.ikna.ui.text.S

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ikna.AppContainer
import dev.ikna.data.catalog.catalogCardReport
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaTextButton

/*
 * The whole screen is the card.
 *
 * Fixed geometry is the point of this layout: the status line, the hint line and
 * the button row all keep their height no matter what is inside them. Nothing
 * appears, disappears or resizes while you answer, so a thumb already moving
 * towards a button never lands on something else — which is exactly what used to
 * happen when the hint above the buttons showed up only on the speaking level.
 *
 * The card itself now runs edge to edge. It used to sit in a 20dp margin with an
 * outline around it, which turned the one thing you are supposed to be reading
 * into a small window in the middle of a screen full of nothing.
 *
 * One bar at the top instead of two rows. It carries the way out, the cost of
 * the session before it starts, and — when the session belongs to one deck —
 * which deck that is.
 *
 * Two answers, and both of them are written down where they cannot leave. See
 * [SwipeableCard]: the words used to live on the card and travelled off screen
 * with it, so the side you were pulling towards was the side whose meaning had
 * just disappeared.
 */

private val BAR_HEIGHT = 44.dp
private val UNDO_HEIGHT = 48.dp
private val EDGE = 20.dp

@Composable
fun SessionScreen(
    container: AppContainer,
    deckId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var reportCard by remember { mutableStateOf<dev.ikna.domain.session.SessionCard?>(null) }
    // Keyed by deck: opening Polish and then English must not hand the second
    // session the first one's queue.
    val vm: SessionViewModel = viewModel(
        key = "session:" + (deckId ?: "all"),
        factory = SessionViewModel.factory(
            container.learningRepository,
            container.settings,
            container.speaker,
            deckId
        )
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    val card = state.current
    ObserveReviewInterruptions(vm.reviewSignals)

    // A review is one of the few places where no touch for a minute can mean
    // concentration rather than absence. Keep the display awake only while an
    // actual card is in front of the user: not during loading, not on the finish
    // screen, and not after this destination leaves composition. View-level
    // keepScreenOn needs no permission and Android ignores it in the background.
    val view = LocalView.current
    val keepScreenAwake = card != null && !state.loading && !state.finished
    DisposableEffect(view, keepScreenAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = previous || keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IknaSessionTopBar(state = state)
        IknaProgress(fraction = state.progress)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                card != null -> SwipeableCard(
                    key = card.card.key + ":" + state.index,
                    revealed = state.revealed,
                    animations = settings.animations,
                    haptics = settings.haptics,
                    // The two words stay on screen until the movement is learned,
                    // then leave it. Read once per session, so nothing appears or
                    // disappears under a thumb that is already moving.
                    railsAtRest = !state.swipeFluent,
                    onReveal = vm::reveal,
                    signals = vm.reviewSignals,
                    onRate = { rating, signals -> vm.rate(rating, viaSwipe = true, signals = signals) }
                ) { progress ->
                    ChunkCard(
                        label = askLabel(card.ask, card.chunk.lang == NO_LANG),
                        prompt = card.prompt,
                        answer = card.answer,
                        // Which part of the sentence the card is actually
                        // asking about. Null at the levels where the front
                        // is not a sentence, which the card handles.
                        promptTarget = card.promptTarget,
                        answerTarget = card.answerTarget,
                        // How it sounds, in whatever notation this deck is set
                        // to. Resolved per card rather than once per session,
                        // because a session draws from every active deck at
                        // once and the setting belongs to the deck -- Polish
                        // and Spanish can want different answers in the same
                        // twenty cards.
                        //
                        // Phonetics.line returns null for a deck with no
                        // transcription, a language the pipeline cannot
                        // transcribe, and a deck switched off, so all three
                        // arrive at the card as the same thing: draw nothing.
                        promptTranscription = Phonetics.line(
                            ipa = card.promptIpa,
                            lang = card.chunk.lang,
                            mode = settings.phoneticsFor(card.chunk.packId)
                        ),
                        answerTranscription = Phonetics.line(
                            ipa = card.answerIpa,
                            lang = card.chunk.lang,
                            mode = settings.phoneticsFor(card.chunk.packId)
                        ),
                        // On a subject deck the third field IS the meaning of the term, so
                        // showing it beside a definition with the term blanked out
                        // would simply print the answer.
                        hint = if (card.ask == Ask.GAP && card.chunk.lang != NO_LANG) {
                            card.meaning
                        } else {
                            null
                        },
                        sourceLabel = card.sourceId?.let { S.t("src.001") + "Tatoeba #" + it },
                        onSource = card.sourceId?.let { id ->
                            { openTatoeba(context, id) }
                        },
                        revealed = state.revealed,
                        // The one line that says how to turn a card over. It was
                        // computed for every session and then never drawn, so the
                        // first card of a first session explained nothing at all.
                        showTapHint = state.showRevealHint,
                        progress = progress,
                        onTap = vm::reveal,
                        tapEnabled = !state.revealed,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> IknaSessionEmptyState(
                    state = state,
                    animations = settings.animations,
                    onExtra = vm::addExtra
                )
            }
        }

        IknaSessionUndoBar(
            visible = state.undoVisible,
            failed = state.undoFailed,
            wrong = state.wrongMarked,
            wrongSourceId = state.wrongSourceId,
            reportCopied = state.wrongReportCopied,
            onUndo = vm::undo,
            onDismiss = vm::dismissUndo,
            onOpenSource = { id -> openTatoeba(context, id) }
        )

        // The way out and the loudspeaker, both within reach of the thumb that is
        // already swiping cards. They used to sit in the top bar, which on this
        // screen meant the exit was the furthest point from the hand doing the
        // work.
        IknaBottomBar {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
            Spacer(Modifier.weight(1f))
            // Only on a turned card, and never as a third rating button: the two
            // words under the thumb are about memory, this is about the deck
            // being wrong. Hidden before the reveal because a card nobody has
            // read yet cannot be judged, and because a mistap here would throw
            // away material instead of grading it.
            if (state.current != null && state.revealed) {
                // An order, not a verdict.
                //
                // This read "this card is wrong" in the muted grey every caption
                // on this screen uses, in a bar under every card that had been
                // turned over -- so it looked like something the app was saying
                // about the card rather than something to press, and a person
                // reading it card after card concludes the app has condemned the
                // whole deck. Upper case and an imperative is how an action is
                // written here already: see sess.013 beside an answer.
                IknaTextButton(
                    label = S.t("sess.044"),
                    onClick = {
                        val current = state.current
                        if (current != null && catalogCardReport(current.chunk) != null) {
                            reportCard = current
                        } else {
                            vm.markWrong()
                        }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
            // Sound is a control, not part of the card. The card is the one thing on
            // this screen that must never grow a control, and the mark is only drawn
            // when pressing it would not give the answer away.
            if (state.speechReady && state.speakable) {
                IknaIconButton(
                    glyph = IknaGlyph.SOUND,
                    onClick = vm::speakCurrent,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = S.t("a11y.005")
                )
            }
        }
    }

    reportCard?.let { pending ->
        IknaDialog(
            title = S.t("src.002"),
            body = S.t("src.003"),
            confirmLabel = S.t("src.004"),
            onConfirm = {
                val copied = catalogCardReport(pending.chunk)?.let { report ->
                    copyReport(context, report)
                } == true
                reportCard = null
                vm.markWrong(reportCopied = copied)
            },
            dismissLabel = S.t("src.005"),
            onDismiss = { reportCard = null }
        )
    }
}

/**
 * Way out on the left, deck on the right, one line of service text between them.
 *
 * The way out is a real 44dp target. It used to be a 20dp invisible strip at the
 * screen edge that opened a drawer — the same strip Android 10 and later claims
 * for its own back gesture, so the app lost that fight roughly every other try,
 * and on this screen the strip was disabled outright, which left the session with
 * no exit at all.
 *
 * The service text used to carry a live "осталось 12 · ~3 мин" and "дальше:
 * вставить" beside the card for the whole session. Both answer questions nobody
 * asks in the middle of recalling something, and a counter ticking down next to
 * the thing you are reading is an invitation to watch the counter instead. So the
 * cost of the session is shown once, before the first answer, where it is a
 * decision — and then the line goes quiet. The one thing that earns a word back
 * is the minimum being reached, for one card only.
 */




/** Fixed strip, so an answer never shifts the buttons under your thumb. */


private fun openTatoeba(context: Context, id: String) {
    val url = tatoebaSentenceUrl(id) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun copyReport(context: Context, report: String): Boolean = runCatching {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ikna catalogue card report", report))
}.isSuccess

/**
 * What the question is asking for.
 *
 * A subject deck says it differently at the same two levels: a neuroscience card
 * shows a term and then its definition with the term missing, and calling that
 * "recognition" and "a gap in a sentence" describes a phrasebook instead. The
 * third level never appears there at all -- see [dev.ikna.domain.session.LevelPromotion].
 */
private fun askLabel(ask: Ask, subject: Boolean): String = when (ask) {
    Ask.RECOGNISE -> if (subject) S.t("sess.046") else S.t("sess.015")
    Ask.GAP -> if (subject) S.t("sess.047") else S.t("sess.016")
    Ask.PRODUCE -> S.t("sess.017")
}

/**
 * The end of a deck outranks the end of a day.
 *
 * "Новые чанки придут сами завтра" is true on almost every empty screen and
 * false on exactly one: the deck with nothing left in it. There, tomorrow brings
 * nothing new, and the app would go on promising it every single day.
 */
