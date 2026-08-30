package dev.ikna.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.domain.fsrs.Rating
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.domain.session.SessionPlan
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import kotlinx.coroutines.launch

/**
 * The cards.
 *
 * Grading is the phone's two answers -- do not know, know -- because that is the
 * interaction this app has, and a desktop build that graded differently would be
 * a different app. The full four FSRS grades are on the number keys for anyone
 * who wants them, which is a thing a keyboard can offer and a thumb cannot.
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
                }
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
                    Key.One, Key.NumPad1 -> if (revealed) { grade(Rating.AGAIN); true } else false
                    Key.Two, Key.NumPad2 -> if (revealed) { grade(Rating.HARD); true } else false
                    Key.Three, Key.NumPad3 -> if (revealed) { grade(Rating.GOOD); true } else false
                    Key.Four, Key.NumPad4 -> if (revealed) { grade(Rating.EASY); true } else false
                    Key.Z -> { undo(); true }
                    else -> false
                }
            }
            .padding(40.dp)
    ) {
        val header = plan?.deckTitle ?: S.t("deck.004")
        Row(Modifier.fillMaxWidth()) {
            Text(header, color = palette.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (cards.isNotEmpty()) {
                Text(
                    (index + 1).toString() + " / " + cards.size,
                    color = palette.muted,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (loading) {
                Text(S.t("sess.001"), color = palette.muted, fontSize = 13.sp)
            } else if (current == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val answeredToday = plan?.answeredToday ?: 0
                    Text(
                        if (answeredToday > 0) S.t("sess.005") else S.t("sess.006"),
                        color = palette.ink,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IknaButton(S.t("sess.009"), palette, filled = true) { addMore() }
                        IknaButton(S.t("sess.008"), palette) { reload += 1 }
                    }
                }
            } else {
                val mode = settings.phoneticsFor(current.chunk.packId)
                Column(
                    Modifier.widthIn(max = 640.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(current.prompt, color = palette.ink, fontSize = 26.sp)

                    val promptLine = Phonetics.line(
                        ipa = current.promptIpa,
                        lang = current.chunk.lang,
                        mode = mode
                    )
                    if (promptLine != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(promptLine, color = palette.muted, fontSize = 15.sp)
                    }

                    if (revealed) {
                        Spacer(Modifier.height(22.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
                        Spacer(Modifier.height(22.dp))
                        Text(current.answer, color = palette.ink, fontSize = 21.sp)

                        val answerLine = Phonetics.line(
                            ipa = current.answerIpa,
                            lang = current.chunk.lang,
                            mode = mode
                        )
                        if (answerLine != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(answerLine, color = palette.muted, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        val message = note
        if (message != null) {
            Text(message, color = palette.muted, fontSize = 11.sp)
            Spacer(Modifier.height(10.dp))
        }

        if (current != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!revealed) {
                    IknaButton(S.t("sess.015"), palette, filled = true) { revealed = true }
                } else {
                    IknaButton(S.t("card.003"), palette) { grade(Rating.AGAIN) }
                    IknaButton(S.t("card.004"), palette, filled = true) { grade(Rating.GOOD) }
                    IknaButton(S.t("sess.044"), palette) { wrong() }
                }
                Box(Modifier.weight(1f))
                IknaButton(S.t("sess.013"), palette) { undo() }
            }
            Spacer(Modifier.height(10.dp))
            Text(S.t("pc.002"), color = palette.muted, fontSize = 10.sp)
        }
    }
}
