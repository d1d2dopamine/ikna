package dev.ikna.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.phonetics.PhoneticsMode
import dev.ikna.ui.decks.iknaCardWord
import dev.ikna.ui.decks.iknaPercentDone
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaTextField
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

    fun commitRename() {
        scope.launch {
            runCatching { container.deckRepository.rename(current.id, title) }
                .onFailure { error -> logLine("rename failed: " + error) }
            reload += 1
            onChanged()
        }
    }

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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
            .widthIn(max = 720.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("dp.013")
            )
        }
        Spacer(Modifier.height(Space.md))
        Text(
            text = current.title,
            style = MaterialTheme.typography.headlineSmall,
            color = palette.ink
        )
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IknaTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = S.t("add.001"),
                onSearch = { commitRename() },
                modifier = Modifier.weight(1f),
                maxLength = 60
            )
            Spacer(Modifier.width(10.dp))
            IknaButton(S.t("sess.014"), palette) { commitRename() }
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.003"), palette)
        Spacer(Modifier.height(4.dp))
        Text(S.t("dp.004"), color = palette.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (code in DECK_LANGS) {
                IknaButton(code.uppercase(), palette, filled = current.lang == code) {
                    scope.launch {
                        runCatching { container.deckRepository.setLang(current.id, code) }
                        reload += 1
                        onChanged()
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        IknaButton(S.t("add.057"), palette, filled = current.lang == NO_LANG) {
            scope.launch {
                runCatching { container.deckRepository.setLang(current.id, NO_LANG) }
                reload += 1
                onChanged()
            }
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.014"), palette)
        Spacer(Modifier.height(4.dp))
        Text(S.t("dp.015"), color = palette.muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IknaButton(S.t("dp.016"), palette, filled = mode == PhoneticsMode.RESPELL) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.RESPELL) }
            }
            IknaButton(S.t("dp.017"), palette, filled = mode == PhoneticsMode.IPA) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.IPA) }
            }
            IknaButton(S.t("dp.018"), palette, filled = mode == PhoneticsMode.OFF) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.OFF) }
            }
        }
        if (!current.hasPhonetics) {
            Spacer(Modifier.height(8.dp))
            Text(S.t("dp.019"), color = palette.muted, style = MaterialTheme.typography.labelMedium)
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("a11y.006"), palette)
        Spacer(Modifier.height(10.dp))
        IknaButton(
            if (current.isActive) S.t("dp.001") else S.t("dp.002"),
            palette,
            filled = current.isActive
        ) {
            scope.launch {
                runCatching { container.deckRepository.setActive(current.id, !current.isActive) }
                reload += 1
                onChanged()
            }
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.010"), palette)
        Spacer(Modifier.height(10.dp))
        IknaButton(if (adding) S.t("dp.011") else S.t("dp.010"), palette) { addCards() }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.006"), palette)
        Spacer(Modifier.height(10.dp))
        IknaButton(S.t("dp.006"), palette) { exportDeck() }

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

        IknaButton(if (confirmDelete) S.t("dp.008") else S.t("dp.007"), palette) {
            if (!confirmDelete) {
                confirmDelete = true
            } else {
                scope.launch {
                    runCatching { container.deckRepository.delete(current.id) }
                    onDeleted()
                }
            }
        }
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
