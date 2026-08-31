package dev.ikna.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.ikna.desktop.anki.AnkiImportError
import dev.ikna.desktop.anki.AnkiImportException
import dev.ikna.desktop.anki.AnkiImportResult
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Importing an Anki package, on the machine where the file already is.
 *
 * The phone has had this screen since the bridge was built; the window shipped
 * with the button greyed out and "not on the computer yet" underneath it, which
 * is the wrong way round -- an .apkg is a file exported by a desktop program and
 * it is nearly always sitting on a desktop when somebody wants it imported.
 *
 * The report is the phone's report, line for line, and for the same reason: an
 * import that says only "done" is an import you have to go and verify by hand.
 * What was skipped and why is the part worth reading.
 */
@Composable
fun AnkiPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onChanged: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AnkiImportResult?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun run(file: File) {
        busy = true
        failure = null
        report = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { container.ankiImporter.importPackage(file, S.lang) }
            }
            busy = false
            outcome
                .onSuccess { done ->
                    report = done
                    onChanged()
                }
                .onFailure { error ->
                    failure = message(error)
                    logLine("anki import failed: " + error)
                }
        }
    }

    fun choose() {
        val file = pickApkg() ?: return
        run(file)
    }

    // A file dropped on the window while this screen is open is the same request
    // as pressing the button, so it does not ask again.
    LaunchedEffect(DesktopDrop.pending) {
        val dropped = DesktopDrop.pending
        if (!busy && dropped != null && dropped.name.endsWith(".apkg", ignoreCase = true)) {
            DesktopDrop.pending = null
            run(dropped)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("add.001"))
        }
        Spacer(Modifier.height(Space.md))
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            SectionTitle(S.t("anki.001"), palette)
            Spacer(Modifier.height(Space.md))
            Note(S.t("anki.002"), palette)
            Note(S.t("anki.003"), palette)

            Spacer(Modifier.height(Space.lg))
            IknaWideButton(
                label = if (busy) S.t("anki.006") else S.t("anki.005"),
                onClick = { choose() },
                modifier = Modifier.widthIn(max = 360.dp),
                filled = true,
                enabled = !busy
            )
            if (busy) {
                Spacer(Modifier.height(Space.sm))
                Note(S.t("anki.007"), palette)
            }

            val error = failure
            if (error != null) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink
                )
                Spacer(Modifier.height(Space.sm))
                Note(S.t("anki.024"), palette)
            }

            val done = report
            if (done != null) {
                Spacer(Modifier.height(Space.lg))
                IknaRule(color = palette.line)
                Spacer(Modifier.height(Space.lg))
                SectionTitle(S.t("anki.025"), palette)
                Spacer(Modifier.height(Space.md))
                Line(S.t("anki.008") + done.decks, palette)
                Line(S.t("anki.009") + done.cards, palette)
                Line(S.t("anki.032") + languages(done.languages), palette)
                Line(S.t("anki.010") + done.reviewEventsImported, palette)
                if (done.reviewEventsSkipped > 0) {
                    Line(S.t("anki.011") + done.reviewEventsSkipped, palette)
                }
                if (done.suspendedOrBuried > 0) {
                    Line(S.t("anki.012") + done.suspendedOrBuried, palette)
                }
                if (done.skippedCards > 0) Line(S.t("anki.013") + done.skippedCards, palette)
                if (done.mediaCards > 0) Line(S.t("anki.014") + done.mediaCards, palette)
                if (done.fallbackCards > 0) Line(S.t("anki.030") + done.fallbackCards, palette)
                if (done.historyWasLimited) {
                    Spacer(Modifier.height(Space.sm))
                    Note(S.t("anki.015"), palette)
                }
                Spacer(Modifier.height(Space.md))
                Note(S.t("anki.028"), palette)
                Note(S.t("anki.029"), palette)
                Spacer(Modifier.height(Space.md))
                IknaWideButton(
                    label = S.t("anki.017"),
                    onClick = { choose() },
                    modifier = Modifier.widthIn(max = 360.dp),
                    enabled = !busy
                )
            }
        }
    }
}

@Composable
private fun Line(text: String, palette: IknaPalette) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = palette.ink)
    Spacer(Modifier.height(Space.xs))
}

@Composable
private fun Note(text: String, palette: IknaPalette) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    Spacer(Modifier.height(Space.xs))
}

/**
 * The languages the decks were decided to be in, in words rather than codes.
 *
 * A person importing does not know what "custom" or "und" mean, and those two
 * are exactly the cases worth reporting: a deck read as a subject will not be
 * read aloud, and an undecided one is waiting for one tap on its own page.
 */
private fun languages(codes: List<String>): String {
    if (codes.isEmpty()) return "-"
    return codes.distinct().joinToString(", ") { code ->
        when (code) {
            "custom" -> S.t("anki.033")
            "und" -> S.t("anki.034")
            else -> code
        }
    }
}

private fun message(error: Throwable): String = when ((error as? AnkiImportException)?.error) {
    AnkiImportError.FILE_TOO_LARGE -> S.t("anki.022")
    AnkiImportError.NOT_APKG -> S.t("anki.018")
    AnkiImportError.NO_COLLECTION -> S.t("anki.019")
    AnkiImportError.UNSUPPORTED_COLLECTION -> S.t("anki.021")
    AnkiImportError.UNREADABLE_DATABASE -> S.t("anki.020")
    AnkiImportError.NO_USABLE_CARDS -> S.t("anki.023")
    AnkiImportError.PLACEHOLDER_COLLECTION -> S.t("anki.031")
    else -> S.t("anki.018")
}

/** Ask for a package to read, with the platform dialog. */
private fun pickApkg(): File? {
    val chooser = JFileChooser()
    chooser.fileFilter = FileNameExtensionFilter("Anki (*.apkg)", "apkg", "colpkg")
    val answer = chooser.showOpenDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
