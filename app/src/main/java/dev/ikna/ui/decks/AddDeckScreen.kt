package dev.ikna.ui.decks

import dev.ikna.ui.text.S

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.pack.SeedProblem
import dev.ikna.data.pack.SeedWarning
import dev.ikna.data.repo.DeckImport
import dev.ikna.data.repo.NO_LANG
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where a deck comes from.
 *
 * The plus used to open the system file browser directly, with no filter on it,
 * which meant the first thing someone saw after deciding to add a deck was their
 * camera roll — and picking a photo from it read the whole file into a string and
 * took the app down with it. Worse than the crash was the silence: nothing on
 * that screen ever said what a deck file is, and the only format it accepted was
 * one JSON object per line with character offsets in it. Nobody writes that by
 * hand. In practice the app shipped with two decks and no way to get a third.
 *
 * This screen exists because the app is for people who lose the thread while
 * doing setup. So the setup is handed to a model: one button copies a prompt that
 * explains the format, the person tells the model what they want to learn, and
 * whatever comes back is pasted into the field below or saved as a file and
 * picked. The format the prompt asks for is three columns separated by a bar,
 * which is also the format a person can type by hand if they would rather.
 *
 * Nothing here validates by refusing. Bad lines are skipped, counted, and the
 * first one is quoted back with the reason, because a report that says only
 * "0 imported" is a dead end.
 */
@Composable
fun AddDeckScreen(
	container: AppContainer,
	onBack: () -> Unit
) {
	val context = LocalContext.current
	val clipboard = LocalClipboardManager.current
	val scope = rememberCoroutineScope()

	var pasted by remember { mutableStateOf("") }
	var note by remember { mutableStateOf<String?>(null) }
	var busy by remember { mutableStateOf(false) }
	var prompt by remember { mutableStateOf("") }
	var subjectPrompt by remember { mutableStateOf("") }
	// Language deck or subject deck. Everything else on this screen follows
	// from it: which prompt is copied, which questions are worth asking, and
	// whether the deck claims a language at all.
	var subject by remember { mutableStateOf(false) }
	var guide by remember { mutableStateOf(false) }
	var lang by remember { mutableStateOf(NO_LANG) }

	// What the prompt used to leave blank for the person to type into a chat
	// window by hand. Answered here instead, with the app's own language as the
	// obvious guess for the meanings.
	var meanings by remember { mutableStateOf(S.lang) }
	var count by remember { mutableStateOf(PROMPT_COUNTS[1]) }
	var level by remember { mutableStateOf(LEVEL_BEGINNER) }
	var topic by remember { mutableStateOf("") }

	// The prompt is an asset rather than a string in the catalogue: it is three
	// kilobytes of English addressed to a model, not interface text, and it has to
	// stay the same in all three languages of the app.
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			prompt = runCatching {
				context.assets.open(PROMPT_ASSET).bufferedReader().use { it.readText() }
			}.getOrDefault("")
			subjectPrompt = runCatching {
				context.assets.open(SUBJECT_ASSET).bufferedReader().use { it.readText() }
			}.getOrDefault("")
		}
	}

	// The prompt as it will actually be sent: the asset with the answers in it.
	val filled = remember(prompt, subjectPrompt, subject, lang, meanings, count, topic, level) {
		if (subject) {
			fillSubjectPrompt(subjectPrompt, meanings, count, topic, level)
		} else {
			fillPrompt(prompt, lang, meanings, count, topic, level)
		}
	}

	suspend fun install(name: String, text: String) {
		busy = true
		val report = withContext(Dispatchers.IO) {
			runCatching {
				container.deckRepository.importText(
					fileName = name,
					text = text,
					fallbackTitle = S.t("deckrepo.001"),
					lang = lang
				)
			}.getOrNull()
		}
		// A deck added now is wanted now, not tomorrow: the day was already planned
		// before this deck existed, so the plan is dropped and rebuilt.
		if (report != null && report.installed > 0) {
			container.learningRepository.invalidatePlan()
		}
		busy = false
		note = if (report == null) S.t("add.019") else describe(report)
	}

	// Only things that can be read as text. The old picker asked for */* and so
	// offered videos, which is both a crash and a lie about what the app accepts.
	// Plain text alone is too narrow — a .jsonl written by the generator tool
	// arrives as application/octet-stream on most devices — so the list is the
	// three types a deck can plausibly be handed over as, and nothing else.
	val picker = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		if (uri == null) return@rememberLauncherForActivityResult
		scope.launch {
			val size = sizeOf(context, uri)
			if (size != null && size > MAX_FILE_BYTES) {
				note = S.t("add.018")
				return@launch
			}
			busy = true
			val read = withContext(Dispatchers.IO) {
				runCatching {
					context.contentResolver.openInputStream(uri)
						?.bufferedReader()?.use { it.readText() }
				}.getOrNull()
			}
			busy = false
			if (read == null) {
				note = S.t("add.019")
				return@launch
			}
			install(displayName(context, uri), read)
		}
	}

	// Saving the prompt goes through the system's own "create a file" sheet, so
	// the app needs no storage permission and the file lands wherever the person
	// keeps things rather than in a folder chosen for them.
	val saver = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain")
	) { uri: Uri? ->
		if (uri == null) return@rememberLauncherForActivityResult
		scope.launch {
			val ok = withContext(Dispatchers.IO) {
				runCatching {
					context.contentResolver.openOutputStream(uri)?.use { out ->
						out.write(filled.toByteArray())
					}
					true
				}.getOrDefault(false)
			}
			note = if (ok) S.t("add.010") else S.t("add.011")
		}
	}

	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	val line = MaterialTheme.colorScheme.outline

	Column(modifier = Modifier.fillMaxSize()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.height(BarHeight)
				.padding(horizontal = Space.sm),
			verticalAlignment = Alignment.CenterVertically
		) {
			IknaIconButton(
				glyph = IknaGlyph.BACK,
				onClick = onBack,
				label = S.t("a11y.001")
			)
			Spacer(Modifier.height(Space.sm))
			Text(
				text = S.t("add.001"),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold,
				modifier = Modifier.padding(start = Space.xs)
			)
		}

		Column(
			modifier = Modifier
				.weight(1f)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = Edge)
		) {
			Spacer(Modifier.height(Space.md))
			Text(
				text = S.t("add.002"),
				style = MaterialTheme.typography.bodyMedium
			)

			Spacer(Modifier.height(Space.md))
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


			Spacer(Modifier.height(Space.xl))
			IknaRule()
			Spacer(Modifier.height(Space.lg))

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

			if (!subject) {
				Text(
					text = S.t("add.038"),
					style = MaterialTheme.typography.labelMedium,
					color = muted
				)
				Spacer(Modifier.height(Space.sm))
				LangChips(current = lang, onPick = { lang = it })

				Spacer(Modifier.height(Space.lg))
				IknaRule()
				Spacer(Modifier.height(Space.lg))
			}

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
				onClick = { saver.launch(PROMPT_FILE) }
			)

			Spacer(Modifier.height(Space.lg))
			IknaRule()
			Spacer(Modifier.height(Space.lg))

			Text(
				text = S.t("add.016"),
				style = MaterialTheme.typography.labelMedium,
				color = muted
			)
			Spacer(Modifier.height(Space.sm))

			// The field is deliberately the same shape as the hex field in settings:
			// a rectangle with a line around it and nothing else in it.
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 140.dp)
					.border(Space.hair, line)
					.padding(Space.md)
			) {
				if (pasted.isEmpty()) {
					Text(
						text = S.t("add.012"),
						style = MaterialTheme.typography.bodyMedium,
						color = muted
					)
				}
				BasicTextField(
					value = pasted,
					onValueChange = { pasted = it.take(MAX_PASTED_CHARS) },
					textStyle = MaterialTheme.typography.bodyMedium.copy(
						color = MaterialTheme.colorScheme.onBackground
					),
					cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
					modifier = Modifier.fillMaxWidth()
				)
			}

			Spacer(Modifier.height(Space.sm))
			Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
				IknaTextButton(
					label = S.t("add.013"),
					onClick = {
						pasted = S.t("add.027")
						note = S.t("add.028")
					}
				)
				if (pasted.isNotEmpty()) {
					IknaTextButton(
						label = S.t("add.029"),
						onClick = { pasted = "" },
						color = muted
					)
				}
			}

			Spacer(Modifier.height(Space.md))
			IknaWideButton(
				label = S.t("add.014"),
				filled = true,
				enabled = !busy,
				onClick = {
					if (pasted.isBlank()) {
						note = S.t("add.020")
					} else {
						scope.launch { install(S.t("add.030"), pasted) }
					}
				}
			)
			Spacer(Modifier.height(Space.sm))
			IknaWideButton(
				label = S.t("add.015"),
				enabled = !busy,
				onClick = { picker.launch(ACCEPTED_TYPES) }
			)

			Spacer(Modifier.height(Space.lg))
			val shown = if (busy) S.t("add.017") else note
			if (shown != null) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surface)
						.padding(Space.md)
				) {
					Text(
						text = shown,
						style = MaterialTheme.typography.bodyMedium
					)
				}
				Spacer(Modifier.height(Space.md))
				IknaWideButton(
					label = S.t("add.026"),
					onClick = onBack,
					enabled = !busy
				)
			}

			Spacer(Modifier.height(Space.xxl))
		}
	}
}

/**
 * A row of answers, four to a line.
 *
 * The same shape as the deck language chips, for the same reason: a strip that
 * scrolls sideways hides half of its options behind a gesture nobody is told
 * about.
 */
@Composable
private fun PromptChoice(
	options: List<String>,
	label: (String) -> String,
	current: String,
	onPick: (String) -> Unit
) {
	options.chunked(4).forEach { group ->
		Row(
			modifier = Modifier.padding(bottom = Space.sm),
			horizontalArrangement = Arrangement.spacedBy(Space.sm)
		) {
			group.forEach { option ->
				IknaChip(
					label = label(option),
					selected = current == option,
					onClick = { onPick(option) }
				)
			}
		}
	}
}

/** One line of the four-line instruction, with the bar the rest of the app uses. */
@Composable
private fun Step(text: String) {
	Row(modifier = Modifier.padding(bottom = Space.sm)) {
		Text(
			text = "\u25AA",
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.primary
		)
		Spacer(Modifier.padding(start = Space.sm))
		Text(
			text = text,
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(start = Space.sm)
		)
	}
}

/**
 * The import, said out loud.
 *
 * Three shapes: nothing landed, some landed, all landed. The first one is the
 * only one that matters, and it is the one the old screen could not describe:
 * it now names the line and what was wrong with it, which is usually enough to
 * see that a model added a heading row or answered in two columns.
 */
internal fun describe(report: DeckImport): String {
	if (report.installed == 0) {
		val problem = report.firstProblem
			?: return S.t("add.025")
		return S.t("add.025") + "\n" + S.t("add.023") + problem.line +
			S.t("add.024") + reason(problem.problem)
	}
	val head = S.t("add.021") + report.installed
	val flagged = flagged(report)
	if (report.skipped == 0) return head + flagged
	val problem = report.firstProblem
		?: return head + S.t("add.022") + report.skipped + flagged
	return head + S.t("add.022") + report.skipped + "\n" +
		S.t("add.023") + problem.line + S.t("add.024") + reason(problem.problem) + flagged
}

/**
 * What landed and is still worth reading before it is learned.
 *
 * Not an error and not a refusal: the deck is installed. It is the only honest
 * thing an app with no network can say about a deck a model wrote in thirty
 * seconds -- these lines look like they were written to reach a number.
 */
private fun flagged(report: DeckImport): String {
	val warning = report.firstWarning ?: return ""
	return "\n" + S.t("add.050") + report.flagged + "\n" +
		S.t("add.023") + warning.line + S.t("add.024") + warningReason(warning.warning)
}

private fun warningReason(warning: SeedWarning): String = when (warning) {
	SeedWarning.DEFINITION_REPEATS_TERM -> S.t("add.051")
	SeedWarning.HEDGED -> S.t("add.052")
	SeedWarning.SAME_MEANING -> S.t("add.053")
	SeedWarning.HAS_NUMBERS -> S.t("add.054")
}

private fun reason(problem: SeedProblem): String = when (problem) {
	SeedProblem.NOT_THREE_COLUMNS -> S.t("add.031")
	SeedProblem.EMPTY_FIELD -> S.t("add.032")
	SeedProblem.PHRASE_NOT_IN_SENTENCE -> S.t("add.033")
	SeedProblem.TOO_LONG -> S.t("add.034")
	SeedProblem.DUPLICATE -> S.t("add.035")
}

internal fun displayName(context: Context, uri: Uri): String {
	context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
		val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
		if (index >= 0 && cursor.moveToFirst()) {
			cursor.getString(index)?.let { return it }
		}
	}
	return uri.lastPathSegment ?: "deck.txt"
}

/**
 * How big the file is before a single byte of it is read.
 *
 * The import reads the whole file into one string, which is fine for a deck and
 * fatal for a video. The picker no longer offers videos, but a file can still be
 * handed to the app by a file manager, and a phone with 200MB of headroom does
 * not survive being asked to hold a 2GB string.
 */
internal fun sizeOf(context: Context, uri: Uri): Long? {
	context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
		val index = cursor.getColumnIndex(OpenableColumns.SIZE)
		if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
			return cursor.getLong(index)
		}
	}
	return null
}

private const val PROMPT_ASSET = "prompt/deck_prompt.txt"

/** The same thing for a deck that is not about a language. */
private const val SUBJECT_ASSET = "prompt/subject_prompt.txt"
private const val PROMPT_FILE = "ikna-deck-prompt.txt"

/** Four megabytes is around forty thousand cards: far more than a deck ever is. */
internal const val MAX_FILE_BYTES = 4L * 1024L * 1024L

/** The same ceiling for the field, in characters rather than bytes. */
private const val MAX_PASTED_CHARS = 400_000

/** A topic is a phrase, not an essay. The rest of the prompt is already long. */
private const val MAX_TOPIC_CHARS = 120

internal val ACCEPTED_TYPES = arrayOf(
	"text/*",
	"application/json",
	"application/octet-stream"
)
