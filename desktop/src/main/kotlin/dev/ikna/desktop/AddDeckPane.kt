package dev.ikna.desktop

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.DeckImport
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

/** A deck file that is larger than this is not a deck; it is a mistake. */
private const val MAX_FILE_BYTES = 4L * 1024L * 1024L

/**
 * Everything behind the plus, which the window did not have at all.
 *
 * On the phone this is how decks arrive: from the catalogue, from text you
 * paste, from a file, or from the prompt you hand to a model. The window shipped
 * with no way in -- you could study decks and rename decks, but never add one,
 * which makes the whole app read-only on a machine where typing is easiest.
 */
@Composable
fun AddDeckPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onChanged: () -> Unit,
    onOpenCatalog: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun describe(report: DeckImport): String {
        val seen = report.installed + report.skipped
        return S.t("add.026") + " \u00b7 " + report.installed.toString() + " / " + seen.toString()
    }

    fun install(fileName: String, text: String, fallbackTitle: String) {
        if (text.isBlank()) return
        busy = true
        note = S.t("add.017")
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val report = container.deckRepository.importText(
                        fileName = fileName,
                        text = text,
                        fallbackTitle = fallbackTitle
                    )
                    container.learningRepository.invalidatePlan()
                    report
                }
            }
            busy = false
            outcome
                .onSuccess { report ->
                    note = describe(report)
                    pasted = ""
                    onChanged()
                }
                .onFailure { error ->
                    note = S.t("add.019")
                    logLine("deck import failed: " + error)
                }
        }
    }

    fun importFromFile() {
        val file = addPickFileForRead() ?: return
        if (file.length() > MAX_FILE_BYTES) {
            note = S.t("add.018")
            return
        }
        val text = runCatching { file.readText() }.getOrNull()
        if (text == null) {
            note = S.t("add.019")
            return
        }
        val stem = file.name.substringBeforeLast('.')
        install(file.name, text, if (stem.isBlank()) S.t("add.001") else stem)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("add.001")
            )
        }
        Spacer(Modifier.height(Space.md))
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            SectionTitle(S.t("add.001"), palette)
            Spacer(Modifier.height(Space.lg))

            IknaWideButton(
                label = S.t("cat.031"),
                onClick = onOpenCatalog,
                modifier = Modifier.widthIn(max = 320.dp),
                filled = true
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = S.t("cat.032"),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )

            Spacer(Modifier.height(Space.lg))
            IknaRule(color = palette.line)
            Spacer(Modifier.height(Space.lg))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .border(1.dp, palette.line)
                    .padding(12.dp)
            ) {
                if (pasted.isEmpty()) {
                    Text(
                        text = S.t("add.012"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
                BasicTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.ink),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(Space.md))
            IknaWideButton(
                label = S.t("add.014"),
                onClick = { install("", pasted, S.t("add.001")) },
                modifier = Modifier.widthIn(max = 320.dp),
                enabled = !busy && pasted.isNotBlank()
            )

            Spacer(Modifier.height(Space.lg))
            IknaRule(color = palette.line)
            Spacer(Modifier.height(Space.lg))

            IknaWideButton(
                label = S.t("add.015"),
                onClick = { importFromFile() },
                modifier = Modifier.widthIn(max = 320.dp),
                enabled = !busy
            )

            val result = note
            if (result != null) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink
                )
            }

            Spacer(Modifier.height(Space.lg))
            IknaRule(color = palette.line)
            Spacer(Modifier.height(Space.lg))

            IknaWideButton(
                label = S.t("anki.001"),
                onClick = {},
                modifier = Modifier.widthIn(max = 320.dp),
                enabled = false
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                text = S.t("pc.001"),
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )

            // Nothing here about the prompt.
            //
            // The phone keeps the model prompt behind a "how this works" line
            // because a phone has nowhere else to put it. In a window that read
            // as a page of instructions followed by two buttons about a text
            // file, on the one screen whose whole job is to add a deck. The
            // prompt still ships inside the app for whoever wants the file; it
            // is simply not part of this screen any more.
        }
    }
}

/** Ask for a file to read, with the platform dialog. */
private fun addPickFileForRead(): File? {
    val chooser = JFileChooser()
    val answer = chooser.showOpenDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

