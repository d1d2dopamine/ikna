package dev.ikna.desktop
import dev.ikna.ui.decks.*
import dev.ikna.data.repo.NO_LANG
import dev.ikna.platform.ClasspathAssets
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaPanel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
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
private const val MAX_TOPIC_CHARS = 120

/**
 * Everything behind the plus, which the window did not have at all.
 *
 * On the phone this is how decks arrive: from the catalogue, from text you
 * paste, from a file, or from the prompt you hand to a model. The window shipped
 * with no way in -- you could study decks and rename decks, but never add one,
 * which makes the whole app read-only on a machine where typing is easiest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDeckPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onChanged: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenAnki: () -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val muted = palette.muted
    val line = palette.line
    var previewOpen by remember { mutableStateOf(false) }
    var modelWay by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(NO_LANG) }
    var meanings by remember { mutableStateOf(S.lang) }
    var count by remember { mutableStateOf(PROMPT_COUNTS[1]) }
    var level by remember { mutableStateOf(LEVEL_BEGINNER) }
    var topic by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var subjectPrompt by remember { mutableStateOf("") }
    LaunchedEffect(modelWay) {
        if (modelWay && prompt.isEmpty()) {
            val templates = withContext(Dispatchers.IO) {
                runCatching {
                    ClasspathAssets.open("prompt/deck_prompt.txt").bufferedReader().use { it.readText() } to
                        ClasspathAssets.open("prompt/subject_prompt.txt").bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            if (templates == null) note = S.t("add.019")
            else { prompt = templates.first; subjectPrompt = templates.second }
        }
    }
    val filled = remember(prompt, subjectPrompt, subject, lang, meanings, count, topic, level) {
        if (subject) fillSubjectPrompt(subjectPrompt, meanings, count, topic, level)
        else fillPrompt(prompt, lang, meanings, count, topic, level)
    }
    fun savePrompt() {
        val chooser = JFileChooser().apply { selectedFile = File("ikna-deck-prompt.txt") }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        val target = chooser.selectedFile
        if (target.exists() && javax.swing.JOptionPane.showConfirmDialog(null,
            S.t("file.001"), S.t("add.008"), javax.swing.JOptionPane.YES_NO_OPTION) != javax.swing.JOptionPane.YES_OPTION) return
        scope.launch {
            note = withContext(Dispatchers.IO) {
                runCatching { target.writeText(filled); S.t("add.010") }.getOrDefault(S.t("add.011"))
            }
        }
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
                        fallbackTitle = fallbackTitle,
                        lang = if (subject) NO_LANG else lang
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

    // A deck file dropped on the window is the same request as choosing one
    // from the dialog, so it is not asked about twice. Packages are not claimed
    // here -- the Anki screen takes those, and Shell has already opened it.
    LaunchedEffect(DesktopDrop.pending) {
        val dropped = DesktopDrop.pending
        val package_ = dropped != null && (
            dropped.name.endsWith(".apkg", ignoreCase = true) ||
                dropped.name.endsWith(".colpkg", ignoreCase = true)
            )
        if (!busy && dropped != null && !package_) {
            DesktopDrop.pending = null
            val text = if (dropped.length() > MAX_FILE_BYTES) null
            else runCatching { dropped.readText() }.getOrNull()
            when {
                dropped.length() > MAX_FILE_BYTES -> note = S.t("add.018")
                text == null -> note = S.t("add.019")
                else -> {
                    val stem = dropped.name.substringBeforeLast('.')
                    install(dropped.name, text, if (stem.isBlank()) S.t("add.001") else stem)
                }
            }
        }
    }

    DesktopScrollablePane(S.t("add.001"), onBack) {

Text(S.t("cat.031"), style = MaterialTheme.typography.labelMedium, color = palette.muted)
Spacer(Modifier.height(Space.md))

IknaWideButton(
    label = S.t("cat.033"),
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

IknaPanel {
	Text(
		text = S.t("add.072"),
		style = MaterialTheme.typography.labelMedium,
		color = muted
	)
	Text(
		text = S.t("add.073"),
		style = MaterialTheme.typography.bodySmall,
		color = muted
	)
}
Spacer(Modifier.height(Space.md))
IknaWideButton(
	label = if (modelWay) S.t("add.037") else S.t("add.074"),
	onClick = { modelWay = !modelWay },
	enabled = !busy
)

if (modelWay) {
	Spacer(Modifier.height(Space.xl))
	IknaRule()
	Spacer(Modifier.height(Space.lg))
	// The four steps are this screen explaining itself, and they were
	// printed in full above the two buttons that do the same thing. Read
	// once, they are never read again, and every later visit had to scroll
	// past them. So they fold away and the buttons come first.
	IknaTextButton(
		label = if (guide) S.t("add.037") else S.t("add.036"),
		onClick = { guide = !guide },
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
	if (guide) {
		Spacer(Modifier.height(Space.md))
		Step(S.t("add.003"))
		Step(S.t("add.004"))
		Step(S.t("add.005"))
		Step(S.t("add.006"))
	}

	// Asked here rather than left to the deck page. A deck without a
	// language cannot be read aloud, and the only screen that could fix
	// that sat behind a deck nobody had opened yet -- so the deck was
	// silent and nothing said why. Here the answer is one tap, on the
	// screen where the deck is being made, and it defaults to the honest
	// one: no language claimed, no voice offered.
	// What kind of deck this is, asked before anything else, because it
	// decides what the rest of the screen is even asking about.
	//
	// The app was built for languages and its core turned out not to care:
	// a card is a unit, a sentence that carries it and what it means, and
	// that describes a definition in neuroscience as well as a phrase in
	// Polish. Only three things were language-specific, and all three are
	// now decided here -- which prompt the model gets, whether the deck
	// claims a language it can be read aloud in, and whether the third way
	// of asking (produce the phrase from its meaning) is used at all.
	Text(
		text = S.t("add.055"),
		style = MaterialTheme.typography.labelMedium,
		color = muted
	)
	Spacer(Modifier.height(Space.sm))
	Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
		IknaChip(
			label = S.t("add.056"),
			selected = !subject,
			onClick = {
				subject = false
				level = LEVEL_BEGINNER
			}
		)
		IknaChip(
			label = S.t("add.057"),
			selected = subject,
			onClick = {
				subject = true
				// A subject deck claims no language: nothing in it is meant
				// to be pronounced, and a voice reading definitions aloud in
				// a guessed accent is worse than silence.
				lang = NO_LANG
				level = LEVEL_BEGINNER
			}
		)
	}
	Spacer(Modifier.height(Space.sm))
	Text(
		text = if (subject) S.t("add.058") else S.t("add.039"),
		style = MaterialTheme.typography.bodySmall,
		color = muted
	)

	Spacer(Modifier.height(Space.lg))
	IknaRule()
	Spacer(Modifier.height(Space.lg))


	Text(
		text = S.t("add.040"),
		style = MaterialTheme.typography.labelMedium,
		color = muted
	)
	Spacer(Modifier.height(Space.sm))
	Text(
		text = S.t("add.049"),
		style = MaterialTheme.typography.bodySmall,
		color = muted
	)

	Spacer(Modifier.height(Space.md))
	Text(
		// On a subject deck this is the language the cards themselves are
		// written in, not the language the meanings are translated into.
		text = if (subject) S.t("add.062") else S.t("add.042"),
		style = MaterialTheme.typography.bodySmall
	)
	Spacer(Modifier.height(Space.sm))
	PromptChoice(
		options = MEANING_LANGS,
		label = { it.uppercase() },
		current = meanings,
		onPick = { meanings = it }
	)

	Text(
		text = S.t("add.041"),
		style = MaterialTheme.typography.bodySmall
	)
	Spacer(Modifier.height(Space.sm))
	PromptChoice(
		options = PROMPT_COUNTS.map { it.toString() },
		label = { it },
		current = count.toString(),
		onPick = { picked -> count = picked.toIntOrNull() ?: count }
	)

	Text(
		text = S.t("add.046"),
		style = MaterialTheme.typography.bodySmall
	)
	Spacer(Modifier.height(Space.sm))
	PromptChoice(
		options = if (subject) SUBJECT_LEVELS else PROMPT_LEVELS,
		label = { option ->
			when (option) {
				LEVEL_BEGINNER -> S.t("add.043")
				LEVEL_TALKING -> S.t("add.044")
				LEVEL_SOME_BACKGROUND -> S.t("add.059")
				else -> S.t("add.045")
			}
		},
		current = level,
		onPick = { level = it }
	)

	Text(
		text = if (subject) S.t("add.060") else S.t("add.047"),
		style = MaterialTheme.typography.bodySmall
	)
	Spacer(Modifier.height(Space.sm))
	Box(
		modifier = Modifier
			.fillMaxWidth()
			.border(Space.hair, line)
			.padding(Space.md)
	) {
		if (topic.isEmpty()) {
			Text(
				text = if (subject) S.t("add.061") else S.t("add.048"),
				style = MaterialTheme.typography.bodyMedium,
				color = muted
			)
		}
		BasicTextField(
			value = topic,
			onValueChange = { topic = it.take(MAX_TOPIC_CHARS) },
			singleLine = true,
			textStyle = MaterialTheme.typography.bodyMedium.copy(
				color = MaterialTheme.colorScheme.onBackground
			),
			cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
			modifier = Modifier.fillMaxWidth()
		)
	}

	Spacer(Modifier.height(Space.md))
	IknaWideButton(
		label = S.t("add.007"),
		filled = true,
		enabled = filled.isNotEmpty() && !busy,
		onClick = {
			clipboard.setText(AnnotatedString(filled))
			note = S.t("add.009")
		}
	)
	Spacer(Modifier.height(Space.sm))
	IknaWideButton(
		label = S.t("add.008"),
		enabled = filled.isNotEmpty() && !busy,
		onClick = { savePrompt() }
	)

}

Spacer(Modifier.height(Space.lg))
IknaRule()
Spacer(Modifier.height(Space.lg))

Text(S.t("add.038"), style = MaterialTheme.typography.labelMedium, color = muted)
Spacer(Modifier.height(Space.sm))
LangChips(lang) { lang = it }
Spacer(Modifier.height(Space.lg))
Text(S.t("add.016"), style = MaterialTheme.typography.labelMedium, color = muted)
Spacer(Modifier.height(Space.sm))
val lineCount = remember(pasted) { if (pasted.isEmpty()) 0 else pasted.count { it == '\n' } + 1 }
val large = pasted.length > 1_200
if (large) {
    IknaPanel {
        Text("$lineCount " + S.t("add.065") + " · " + pasted.length + " " + S.t("add.066"),
            style = MaterialTheme.typography.bodyMedium)
        IknaTextButton(S.t(if (previewOpen) "add.068" else "add.067"), onClick = { previewOpen = !previewOpen })
        if (previewOpen) {
            val head = remember(pasted) { pasted.lineSequence().take(40).joinToString("\n") }
            Text(head, style = MaterialTheme.typography.bodySmall, color = muted,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
            if (lineCount > 40) Text(S.t("add.069") + (lineCount - 40), style = MaterialTheme.typography.labelSmall, color = muted)
        }
    }
} else {
    Box(Modifier.fillMaxWidth().heightIn(min = 132.dp, max = 220.dp).border(Space.hair, line).padding(Space.md)) {
        if (pasted.isEmpty()) Text(S.t("add.012"), style = MaterialTheme.typography.bodyMedium, color = muted)
        BasicTextField(pasted, onValueChange = {
            pasted = it.take(1_600_000); previewOpen = false
            note = if (it.length > 1_600_000) S.t("add.071") else null
        }, textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.ink),
            cursorBrush = SolidColor(palette.ink), modifier = Modifier.fillMaxWidth())
    }
}
FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
    IknaTextButton(S.t("add.063"), onClick = {
        val text = clipboard.getText()?.text.orEmpty()
        if (text.isBlank()) note = S.t("add.064") else {
            pasted = text.take(1_600_000); previewOpen = false
            note = if (text.length > 1_600_000) S.t("add.071") else null
        }
    })
    IknaTextButton(S.t("add.013"), onClick = { pasted = S.t("add.027"); previewOpen = false; note = S.t("add.028") })
    if (pasted.isNotEmpty()) IknaTextButton(S.t("add.029"), onClick = { pasted = ""; previewOpen = false; note = null })
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
    onClick = onOpenAnki,
    modifier = Modifier.widthIn(max = 320.dp),
    enabled = !busy
)
Spacer(Modifier.height(Space.sm))
Text(
    text = S.t("anki.026"),
    style = MaterialTheme.typography.bodySmall,
    color = palette.muted
)


    }
}

/** Ask for a file to read, with the platform dialog. */
private fun addPickFileForRead(): File? {
    val chooser = JFileChooser()
    val answer = chooser.showOpenDialog(null)
    return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
