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
 * Save everything, restore everything, and move an install between machines.
 *
 * The phone has written a weekly review log to shared storage for a long time,
 * because the log is the only thing in the database that cannot be regenerated.
 * What it did not have was one action that produces one file: the log was in
 * Documents, the settings were in a second file beside it, and the decks were
 * nowhere. Restoring meant remembering all three.
 *
 * Sync lives here rather than on a screen of its own because it is the same
 * file and the same code path. There is no merge dialog and no "which side
 * wins", and that is not a simplification -- answers are timestamped events, so
 * two devices produce two sets of events, and the union of those sets is the
 * truth. Recomputing the schedule from the union is what the restore already
 * does, so carrying one file back and forth converges instead of fighting.
 */
@Composable
fun BackupPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onChanged: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<BundleWrite?>(null) }
    var loaded by remember { mutableStateOf<BundleRead?>(null) }
    var phone by remember { mutableStateOf<List<String>>(emptyList()) }
    var failure by remember { mutableStateOf("") }

    fun restore(file: File) {
        if (busy) return
        busy = true
        saved = null
        phone = emptyList()
        failure = ""
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { IknaBundle.read(container, file) }
            }
            busy = false
            val read = result.getOrNull()
            if (read == null || read.kind == BundleKind.UNKNOWN) {
                loaded = null
                failure = S.t("bk.013")
                logLine("restore failed: " + result.exceptionOrNull())
            } else {
                loaded = read
                onChanged()
            }
        }
    }

    // Arriving here by dropping a file on the window: import it rather than
    // making the person find it again in a dialog.
    LaunchedEffect(DesktopDrop.pending) {
        val dropped = DesktopDrop.pending
        if (dropped != null && !busy) {
            DesktopDrop.pending = null
            restore(dropped)
        }
    }

    fun save() {
        if (busy) return
        val target = chooseSaveTarget(container) ?: return
        busy = true
        loaded = null
        phone = emptyList()
        failure = ""
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { IknaBundle.write(container, target) }
            }
            busy = false
            saved = result.getOrNull()
            if (saved == null) {
                failure = S.t("bk.013")
                logLine("bundle write failed: " + result.exceptionOrNull())
            }
        }
    }

    fun forPhone() {
        if (busy) return
        busy = true
        saved = null
        loaded = null
        failure = ""
        scope.launch {
            val target = File(container.home, "export")
            val result = runCatching {
                withContext(Dispatchers.IO) { IknaBundle.writeForPhone(container, target) }
            }
            busy = false
            phone = result.getOrNull().orEmpty()
            if (phone.isEmpty()) {
                failure = S.t("bk.014")
                logLine("phone export produced nothing: " + result.exceptionOrNull())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(Space.md)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
        Spacer(Modifier.height(Space.md))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            SectionTitle(S.t("bk.001"), palette)
            Spacer(Modifier.height(Space.md))
            BackupNote(S.t("bk.016"), palette)
            Spacer(Modifier.height(Space.lg))

            IknaWideButton(
                label = if (busy) S.t("bk.005") else S.t("bk.002"),
                onClick = { save() },
                modifier = Modifier.widthIn(max = 360.dp),
                filled = true,
                enabled = !busy
            )
            Spacer(Modifier.height(Space.sm))
            IknaWideButton(
                label = S.t("bk.003"),
                onClick = {
                    val picked = chooseRestoreSource()
                    if (picked != null) restore(picked)
                },
                modifier = Modifier.widthIn(max = 360.dp),
                enabled = !busy
            )
            Spacer(Modifier.height(Space.sm))
            IknaWideButton(
                label = S.t("bk.004"),
                onClick = { forPhone() },
                modifier = Modifier.widthIn(max = 360.dp),
                quiet = true,
                enabled = !busy
            )

            if (failure.isNotBlank()) {
                Spacer(Modifier.height(Space.md))
                BackupLine(failure, palette)
            }

            val done = saved
            if (done != null) {
                Spacer(Modifier.height(Space.md))
                BackupLine(S.t("bk.006") + done.path, palette)
                BackupLine(S.t("bk.007") + done.reviews, palette)
                BackupLine(S.t("bk.008") + done.decks, palette)
                BackupLine(S.t("bk.024") + megabytesOf(done.bytes), palette)
            }

            val back = loaded
            if (back != null) {
                Spacer(Modifier.height(Space.md))
                BackupLine(kindLabel(back.kind), palette)
                if (back.imported > 0) BackupLine(S.t("bk.009") + back.imported, palette)
                if (back.skipped > 0) BackupLine(S.t("bk.010") + back.skipped, palette)
                if (back.replayed > 0) BackupLine(S.t("bk.011") + back.replayed, palette)
                if (back.decks > 0) BackupLine(S.t("bk.008") + back.decks, palette)
                if (back.settings) BackupLine(S.t("bk.012"), palette)
            }

            if (phone.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                for (name in phone) BackupLine(S.t("bk.023") + name, palette)
                Spacer(Modifier.height(Space.xs))
                BackupNote(S.t("bk.019"), palette)
            }

            Spacer(Modifier.height(Space.lg))
            IknaRule(color = palette.line)
            Spacer(Modifier.height(Space.lg))
            SectionTitle(S.t("bk.017"), palette)
            Spacer(Modifier.height(Space.md))
            BackupNote(S.t("bk.018"), palette)
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun BackupLine(text: String, palette: IknaPalette) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = palette.ink)
    Spacer(Modifier.height(Space.xs))
}

@Composable
private fun BackupNote(text: String, palette: IknaPalette) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = palette.muted)
    Spacer(Modifier.height(Space.xs))
}

/** Which of the shapes the picked file turned out to be, in words. */
private fun kindLabel(kind: BundleKind): String = when (kind) {
    BundleKind.BUNDLE -> S.t("bk.015")
    BundleKind.REVIEWS -> S.t("bk.020")
    BundleKind.SETTINGS -> S.t("bk.021")
    BundleKind.DECK -> S.t("bk.022")
    BundleKind.UNKNOWN -> S.t("bk.013")
}

private fun megabytesOf(bytes: Long): String {
    val tenths = (bytes * 10L) / (1024L * 1024L)
    return (tenths / 10L).toString() + "." + (tenths % 10L).toString()
}

/**
 * The save dialog, pre-filled with a dated name in the app folder.
 *
 * Pre-filling is not decoration: a backup the user has to name is a backup
 * called "ikna" that overwrites last month's.
 */
private fun chooseSaveTarget(container: DesktopContainer): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = S.t("bk.002")
    chooser.selectedFile = File(container.home, IknaBundle.defaultName())
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val picked = chooser.selectedFile ?: return null
    return if (picked.name.endsWith(IknaBundle.EXTENSION, ignoreCase = true)) {
        picked
    } else {
        File(picked.absolutePath + IknaBundle.EXTENSION)
    }
}

/**
 * The open dialog accepts the phone's two files as well as our own bundle,
 * because those are the files people actually have lying around.
 */
private fun chooseRestoreSource(): File? {
    val chooser = JFileChooser()
    chooser.dialogTitle = S.t("bk.003")
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    chooser.fileFilter = FileNameExtensionFilter(
        S.t("bk.001"),
        "ikna",
        "jsonl",
        "json",
        "txt"
    )
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.takeIf { it.isFile }
}
