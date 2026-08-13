package dev.ikna.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import dev.ikna.AppContainer
import dev.ikna.audio.SpeechSource
import dev.ikna.audio.VoiceModelInstall
import dev.ikna.audio.VoiceModelKind
import dev.ikna.audio.VoiceModelProblem
import dev.ikna.audio.VoiceModelResult
import dev.ikna.audio.VoiceModelStore
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Which voices speak, and where they came from.
 *
 * This screen exists because the feature used to be a switch and two sliders, and
 * nothing anywhere said whether a card was being read by the phone's own engine,
 * by a model the user added, or by nothing at all. So the top line is a sentence:
 * this is who is speaking. Everything under it is there to change that sentence,
 * and the button that proves it is a button that talks.
 *
 * From 0.5.0 the middle of the screen is a list rather than a slot. Somebody
 * learning two languages needs two models, and before this the second one
 * destroyed the first. Each row carries the three things that were impossible to
 * say with one slot: whether this model is used, which language it reads, and
 * whether it can go away without taking the others with it.
 */
@Composable
fun VoiceScreen(
    container: AppContainer,
    speechEnabled: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { VoiceModelStore(context) }

    var models by remember { mutableStateOf<List<VoiceModelInstall>>(emptyList()) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    /** Deck language -> who would read it. Filled by refresh(), never guessed. */
    var voices by remember { mutableStateOf<List<Pair<String, SpeechSource>>>(emptyList()) }

    val runtime = container.speaker.canLoadModels

    // Asking whether a model loads costs seconds, and it is the answer somebody
    // opened this screen for, so it is asked on arrival and after every change.
    suspend fun refresh() {
        val all = store.installed()
        models = all
        val active = all.firstOrNull { it.enabled }
        ready = if (active == null || !runtime) false
        else container.speaker.modelSpeaks(active.lang ?: "en")

        // Asked per deck language rather than once: this is the difference
        // between a model that works and a model that works on your decks.
        voices = runCatching { container.deckRepository.decks() }
            .getOrDefault(emptyList())
            .map { it.lang }
            .filter { it.isNotBlank() }
            .distinct()
            .map { lang -> lang to container.speaker.sourceFor(lang) }
    }

    LaunchedEffect(Unit) { refresh() }

    /**
     * Every change to a model goes through here, because every one of them has the
     * same two consequences: audio already rendered was rendered by whoever spoke
     * before, and what the screen says about readiness is now a guess.
     */
    fun change(work: suspend () -> Unit) {
        scope.launch {
            work()
            container.speaker.clearCache()
            ready = null
            refresh()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            copied = 0
            note = null
            ready = null
            val result = store.install(uri) { done -> copied = done }
            busy = false
            note = when (result) {
                is VoiceModelResult.Installed -> S.t("voice.024")
                is VoiceModelResult.Refused -> explain(result.problem)
                is VoiceModelResult.Failed -> S.t("voice.023") + (result.message ?: "")
            }
            container.speaker.clearCache()
            refresh()
        }
    }

    // One file rather than a folder, and "*/*" rather than a bzip2 mime type:
    // phones disagree about what a .tar.bz2 is called and several of them answer
    // "nothing at all", which is how a picker ends up greying out the very file it
    // was opened to choose.
    val archivePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            copied = 0
            note = null
            ready = null
            val result = store.installArchive(uri) { done -> copied = done }
            busy = false
            note = when (result) {
                is VoiceModelResult.Installed -> S.t("voice.024")
                is VoiceModelResult.Refused -> explain(result.problem)
                is VoiceModelResult.Failed -> S.t("voice.023") + (result.message ?: "")
            }
            container.speaker.clearCache()
            refresh()
        }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val active = models.firstOrNull { it.enabled }

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
            Text(
                text = S.t("voice.001"),
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
            // ---- switched on at all ---------------------------------------
            //
            // The switch used to live one screen back, in settings, and the test
            // button below does not go through it. So a model could be added,
            // proved out loud on this very screen, and every card still stayed
            // silent -- with nothing anywhere connecting the two facts.
            Spacer(Modifier.height(Space.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = S.t("voice.030"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(Space.hair))
                    Text(
                        text = S.t("voice.031"),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }
                Spacer(Modifier.width(Space.sm))
                IknaToggle(
                    checked = speechEnabled,
                    onCheckedChange = { on ->
                        scope.launch {
                            container.settings.setSpeechEnabled(on)
                            if (!on) container.speaker.stop()
                        }
                    },
                    label = S.t("voice.030")
                )
            }

            Spacer(Modifier.height(Space.lg))
            IknaRule()

            // ---- who is speaking ------------------------------------------
            Spacer(Modifier.height(Space.md))
            Text(
                text = S.t("voice.002"),
                style = MaterialTheme.typography.labelMedium,
                color = muted
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = if (ready == true && active != null) active.name else S.t("voice.003"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = when {
                    !speechEnabled -> S.t("voice.025")
                    ready == null -> S.t("voice.007")
                    ready == true -> S.t("voice.008")
                    active != null -> S.t("voice.005")
                    runtime -> S.t("voice.006")
                    else -> S.t("voice.004")
                },
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            Spacer(Modifier.height(Space.lg))
            IknaWideButton(
                label = S.t("voice.010"),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        val lang = active?.lang ?: "en"
                        container.speaker.speak(sampleFor(lang), lang)
                    }
                }
            )

            Spacer(Modifier.height(Space.xl))
            IknaRule()
            Spacer(Modifier.height(Space.lg))

            // ---- the models -----------------------------------------------
            Text(
                text = S.t("voice.011"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = if (runtime) S.t("voice.012") else S.t("voice.016"),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )

            if (runtime) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("voice.044"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }

            // Said once there is something to say it about: with two models
            // installed, "one per language" stops being trivia and starts being
            // the reason one of them just switched itself off.
            if (models.size > 1) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("voice.040"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }

            models.forEach { model ->
                Spacer(Modifier.height(Space.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(Space.hair))
                        Text(
                            text = kindOf(model.kind) + " \u00b7 " + megabytes(model.bytes) +
                                if (model.enabled) "" else " \u00b7 " + S.t("voice.038"),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted
                        )
                    }
                    Spacer(Modifier.width(Space.sm))
                    // Off keeps the files. "This voice is worse than my phone's"
                    // should cost one tap to act on and one to take back, not
                    // sixty megabytes of copying.
                    IknaToggle(
                        checked = model.enabled,
                        onCheckedChange = { on ->
                            change { store.setEnabled(model.slug, on) }
                        },
                        enabled = !busy,
                        label = model.name
                    )
                }

                // A release that names no language -- every multi-language one --
                // has to be told, or it either speaks everything or nothing.
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = S.t("voice.014"),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    LANGS.forEach { code ->
                        IknaChip(
                            label = code?.uppercase() ?: S.t("voice.015"),
                            selected = model.lang == code,
                            modifier = Modifier.weight(1f),
                            onClick = { change { store.setLanguage(model.slug, code) } }
                        )
                    }
                }

                // Kokoro holds around a hundred voices addressed by number and by
                // nothing else -- they have no names to list. A number and the
                // test button are the whole interface there is to have.
                if (model.kind == VoiceModelKind.KOKORO) {
                    Spacer(Modifier.height(Space.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = S.t("voice.039") + " " + model.speaker,
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            modifier = Modifier.weight(1f)
                        )
                        IknaTextButton(
                            label = "\u2212",
                            enabled = !busy && model.speaker > 0,
                            onClick = { change { store.setSpeaker(model.slug, model.speaker - 1) } }
                        )
                        Spacer(Modifier.width(Space.sm))
                        IknaTextButton(
                            label = "+",
                            enabled = !busy,
                            onClick = { change { store.setSpeaker(model.slug, model.speaker + 1) } }
                        )
                    }
                }

                Spacer(Modifier.height(Space.xs))
                IknaTextButton(
                    label = S.t("voice.013"),
                    enabled = !busy,
                    onClick = {
                        change {
                            store.remove(model.slug)
                            note = null
                        }
                    }
                )
            }

            Spacer(Modifier.height(Space.lg))
            IknaWideButton(
                label = if (models.isEmpty()) S.t("voice.029") else S.t("voice.017"),
                enabled = !busy && runtime,
                onClick = { picker.launch(null) }
            )

            Spacer(Modifier.height(Space.sm))
            IknaWideButton(
                label = S.t("voice.041"),
                enabled = !busy && runtime,
                quiet = true,
                onClick = { archivePicker.launch(arrayOf("*/*")) }
            )

            if (busy) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = S.t("voice.018") + copied,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }

            note?.let { text ->
                Spacer(Modifier.height(Space.md))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ---- who reads which deck -------------------------------------
            //
            // One line per deck language. A model speaks one language; a deck in
            // another falls through to the phone, and a deck in a language the
            // phone has no voice for is silence. All three used to look the same
            // from here, which is why "the test button works and my cards do not"
            // had nowhere to be answered.
            if (voices.isNotEmpty()) {
                Spacer(Modifier.height(Space.xl))
                IknaRule()
                Spacer(Modifier.height(Space.lg))
                Text(
                    text = S.t("voice.032"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("voice.036"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                voices.forEach { (lang, source) ->
                    Spacer(Modifier.height(Space.md))
                    Text(
                        text = if (lang == CUSTOM_LANG) S.t("voice.037")
                        else lang.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = muted
                    )
                    Spacer(Modifier.height(Space.hair))
                    Text(
                        text = when (source) {
                            SpeechSource.MODEL -> S.t("voice.033")
                            SpeechSource.PHONE -> S.t("voice.034")
                            SpeechSource.NOBODY -> S.t("voice.035")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(Space.xl))
            IknaRule()
            Spacer(Modifier.height(Space.lg))
            Text(
                text = S.t("voice.026"),
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** What a deck with no language of its own is stored as. See DeckRepository. */
private const val CUSTOM_LANG = "custom"

/** RU, EN, PL, and "any" -- the honest setting for a multi-language model. */
private val LANGS = listOf("ru", "en", "pl", null)

private fun kindOf(kind: VoiceModelKind): String = when (kind) {
    VoiceModelKind.KOKORO -> "Kokoro"
    VoiceModelKind.VITS -> "Piper / VITS"
}

private fun megabytes(bytes: Long): String = (bytes / 1_000_000L).toString() + " MB"

/**
 * The phrase the test button says.
 *
 * Kept in code rather than in the string catalogues on purpose: it has to be in
 * the language of the model, not of the interface, or a Polish voice gets tested
 * with a Russian sentence and judged for it.
 */
private fun sampleFor(lang: String): String = when (lang.take(2).lowercase()) {
    "ru" -> "\u0422\u0430\u043a \u0437\u0432\u0443\u0447\u0438\u0442 \u0433\u043e\u043b\u043e\u0441."
    "pl" -> "Tak brzmi ten g\u0142os."
    else -> "This is how the voice sounds."
}

private fun explain(problem: VoiceModelProblem): String = when (problem) {
    VoiceModelProblem.NOT_A_MODEL -> S.t("voice.019")
    VoiceModelProblem.NESTED -> S.t("voice.020")
    VoiceModelProblem.TOO_MANY_MODELS -> S.t("voice.021")
    VoiceModelProblem.NO_TOKENS -> S.t("voice.022")
    VoiceModelProblem.NO_VOICES -> S.t("voice.027")
    VoiceModelProblem.NO_PHONEMES -> S.t("voice.028")
    VoiceModelProblem.NOT_AN_ARCHIVE -> S.t("voice.042")
    VoiceModelProblem.NO_SPACE -> S.t("voice.043")
}
