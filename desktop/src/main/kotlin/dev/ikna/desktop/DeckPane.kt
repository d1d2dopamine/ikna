package dev.ikna.desktop
import dev.ikna.ui.decks.IknaDeckAppearance
import dev.ikna.ui.decks.LangChips
import dev.ikna.data.prefs.lookFor
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.SolidColor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.domain.phonetics.PhoneticsMode
import dev.ikna.ui.decks.iknaCardWord
import dev.ikna.ui.decks.iknaPercentDone
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/** The languages the transcription pipeline covers, plus the subject-deck case. */
private val DECK_LANGS = listOf("en", "ru", "pl", "es", "fr", "de", "it", "pt")

/** A file bigger than this is not a list of cards; it is a mistake. */
private const val DECK_MAX_FILE_BYTES = 4L * 1024L * 1024L

/**
 * One deck's settings.
 *
 * This is where the transcription switch lives -- per deck, in the deck's own
 * settings, which is what was asked for: not at download time and not a global
 * preference, because a session draws from several decks at once and Polish and
 * Spanish can reasonably want different answers.
 */
@Composable
fun DeckPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String,
    onChanged: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var deck by remember(deckId) { mutableStateOf<DeckSummary?>(null) }
    var title by remember(deckId) { mutableStateOf("") }
    var confirmDelete by remember(deckId) { mutableStateOf(false) }
    var reload by remember(deckId) { mutableStateOf(0) }
    var adding by remember(deckId) { mutableStateOf(false) }
    var note by remember(deckId) { mutableStateOf<String?>(null) }

    LaunchedEffect(deckId, reload) {
        val loaded = runCatching { container.deckRepository.deck(deckId) }.getOrNull()
        deck = loaded
        title = loaded?.title ?: ""
    }

    val current = deck
    if (current == null) {
        Centered(S.t("sess.001"), palette)
        return
    }

    val mode = settings.phoneticsFor(current.id)



    // Adding to a deck that exists, which the phone can do and the window could not.
    fun addCards() {
        val file = deckPickFileForRead() ?: return
        if (file.length() > DECK_MAX_FILE_BYTES) {
            note = S.t("add.018")
            return
        }
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null) {
            note = S.t("add.019")
            return
        }
        adding = true
        note = S.t("dp.011")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val report = container.deckRepository.importText(
                        fileName = file.name,
                        text = text,
                        fallbackTitle = current.title,
                        appendTo = current.id
                    )
                    container.learningRepository.invalidatePlan()
                    report
                }
            }
            adding = false
            outcome
                .onSuccess { report ->
                    note = S.t("add.026") + " \u00b7 " + report.installed.toString()
                    reload += 1
                    onChanged()
                }
                .onFailure { error ->
                    note = S.t("add.019")
                    logLine("deck append failed: " + error)
                }
        }
    }

    // Sending the deck out as text, the phone's share turned into a file dialog.
    fun exportDeck() {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { container.deckRepository.exportText(current.id) }.getOrNull()
            }
            if (text == null) {
                note = S.t("share.003")
                return@launch
            }
            if (text.isBlank()) {
                note = S.t("share.004")
                return@launch
            }
            val target = deckPickFileForSave(current.id + ".txt") ?: return@launch
            val saved = withContext(Dispatchers.IO) { runCatching { target.writeText(text) } }
            if (saved.isSuccess) {
                note = S.t("add.026")
            } else {
                note = S.t("share.003")
                logLine("deck export failed: " + saved.exceptionOrNull())
            }
        }
    }

    DesktopScrollablePane(current.title, onBack) {
        Spacer(Modifier.height(Space.xs))
        Text(
            text = current.total.toString() + " " + iknaCardWord(current.total) +
                "   " + iknaPercentDone(current.introduced, current.total),
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
        Spacer(Modifier.height(Space.sm))
        IknaProgress(
            if (current.total <= 0) 0f else current.introduced.toFloat() / current.total.toFloat(),
            Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.013"), palette)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().border(Space.hair, palette.line).padding(Space.md)) {
            BasicTextField(value = title, onValueChange = { raw ->
                val value = raw.take(dev.ikna.data.repo.DeckRepository.MAX_TITLE)
                title = value; deck = deck?.copy(title = value)
                scope.launch {
                    runCatching { container.deckRepository.rename(deckId, value) }
                        .onFailure { logLine("rename failed: " + it) }
                    onChanged()
                }
            }, singleLine = true, textStyle = MaterialTheme.typography.titleMedium.copy(color = palette.ink),
                cursorBrush = SolidColor(palette.ink), modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.003"), palette)
        Spacer(Modifier.height(4.dp))
        Text(S.t("dp.004"), color = palette.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        LangChips(current.lang) { code ->
            scope.launch {
                container.deckRepository.setLang(current.id, code)
                reload += 1; onChanged()
            }
        }
        Spacer(Modifier.height(Space.lg)); IknaRule(); Spacer(Modifier.height(Space.lg))
        val muted = palette.muted
        if (current.hasPhonetics && current.lang in Phonetics.SUPPORTED) {
            val mode = settings.phoneticsFor(deckId)

            Text(
                text = S.t("dp.014"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = S.t("dp.015"),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            Spacer(Modifier.height(Space.md))

            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                IknaChip(
                    label = S.t("dp.016"),
                    selected = mode == PhoneticsMode.RESPELL,
                    onClick = {
                        scope.launch {
                            container.settings.setDeckPhonetic(
                                deckId,
                                PhoneticsMode.RESPELL
                            )
                        }
                    }
                )
                IknaChip(
                    label = S.t("dp.017"),
                    selected = mode == PhoneticsMode.IPA,
                    onClick = {
                        scope.launch {
                            container.settings.setDeckPhonetic(
                                deckId,
                                PhoneticsMode.IPA
                            )
                        }
                    }
                )
                IknaChip(
                    label = S.t("dp.018"),
                    selected = mode == PhoneticsMode.OFF,
                    onClick = {
                        scope.launch {
                            container.settings.setDeckPhonetic(
                                deckId,
                                PhoneticsMode.OFF
                            )
                        }
                    }
                )
            }

            Spacer(Modifier.height(Space.md))

            // What the choice looks like, drawn by the same renderer
            // the card uses rather than typed out beside it. A
            // sample written by hand drifts out of agreement with
            // the code; one that goes through the same function
            // cannot.
            Text(
                text = Phonetics.sample(current.lang, mode)
                    ?: S.t("dp.019"),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            Spacer(Modifier.height(Space.lg))
            IknaRule()
            Spacer(Modifier.height(Space.lg))
        }
        IknaDeckAppearance(settings.lookFor(deckId)) { label, tint ->
            scope.launch { container.settings.setDeckLook(deckId, label, tint) }
        }
        SectionTitle(S.t("dp.010"), palette)
        Spacer(Modifier.height(10.dp))
        IknaWideButton(if (adding) S.t("dp.011") else S.t("dp.010"), enabled = !adding, onClick = { addCards() })

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.006"), palette)
        Spacer(Modifier.height(10.dp))
        IknaWideButton(S.t("dp.006"), onClick = { exportDeck() })

        val outcome = note
        if (outcome != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = outcome,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink
            )
        }

        Spacer(Modifier.height(36.dp))

        IknaTextButton(if (confirmDelete) S.t("dp.008") else S.t("dp.007"), color = palette.accent, onClick = {
            if (!confirmDelete) {
                confirmDelete = true
            } else {
                scope.launch {
                    runCatching {
                        container.deckRepository.delete(current.id)
                        container.learningRepository.invalidatePlan()
                    }.onSuccess { onDeleted() }.onFailure { note = S.t("set.057") }
                }
            }
        })
        if (confirmDelete) {
            Spacer(Modifier.height(8.dp))
            Text(S.t("dp.009"), color = palette.muted, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(40.dp))

    }
}

/** Ask for a file of cards to fold into this deck. */
private fun deckPickFileForRead(): File? {
    val chooser = JFileChooser()
    val answer = chooser.showOpenDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Ask where to write this deck out. */
private fun deckPickFileForSave(suggested: String): File? {
    val chooser = JFileChooser()
    chooser.selectedFile = File(suggested)
    val answer = chooser.showSaveDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
