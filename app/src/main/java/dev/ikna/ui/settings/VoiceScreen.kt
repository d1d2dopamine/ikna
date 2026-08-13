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
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Which voice speaks, and where it came from.
 *
 * This screen exists because the previous version of the feature was a switch and
 * two sliders, and nothing anywhere said whether a card was being read by the
 * phone's own engine, by a model inside the app, or by nothing at all. An APK
 * half a gigabyte larger looked exactly like one without it. That is not a
 * missing detail, that is the whole feature being invisible.
 *
 * So the top line is a sentence: this is who is speaking. Everything under it is
 * there to change that sentence, and the button that proves it is a button that
 * talks.
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

    var install by remember { mutableStateOf<VoiceModelInstall?>(null) }
    var ready by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }

    /** Deck language -> who would read it. Filled by refresh(), never guessed. */
    var voices by remember { mutableStateOf<List<Pair<String, SpeechSource>>>(emptyList()) }

    val runtime = container.speaker.canLoadModels

    // Asking whether the model loads costs seconds, and it is the answer somebody
    // opened this screen for, so it is asked on arrival and after every change.
    suspend fun refresh() {
        val current = store.installed()
        install = current
        ready = if (current == null || !runtime) false
        else container.speaker.modelSpeaks(current.lang ?: "en")

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
            // Whatever is already cached was spoken by whoever spoke before.
            container.speaker.clearCache()
            refresh()
        }
    }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val model = install

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
                text = if (ready == true && model != null) model.name else S.t("voice.003"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = when {
                    !speechEnabled -> S.t("voice.025")
                    ready == null -> S.t("voice.007")
                    ready == true -> S.t("voice.008")
                    model != null -> S.t("voice.005")
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
                        val lang = model?.lang ?: "en"
                        container.speaker.speak(sampleFor(lang), lang)
                    }
                }
            )

            Spacer(Modifier.height(Space.xl))
            IknaRule()
            Spacer(Modifier.height(Space.lg))

            // ---- the model ------------------------------------------------
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

            if (model != null) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Space.hair))
                Text(
                    text = kindOf(model.kind) + " \u00b7 " + megabytes(model.bytes) +
                        " \u00b7 " + model.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )

                // A release that names no language -- every multi-language one --
                // has to be told, or it either speaks everything or nothing.
                Spacer(Modifier.height(Space.md))
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
                            onClick = {
                                scope.launch {
                                    store.setLanguage(code)
                                    container.speaker.clearCache()
                                    refresh()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.lg))
            IknaWideButton(
                label = if (model == null) S.t("voice.029") else S.t("voice.017"),
                enabled = !busy && runtime,
                onClick = { picker.launch(null) }
            )

            if (model != null) {
                Spacer(Modifier.height(Space.xs))
                IknaWideButton(
                    label = S.t("voice.013"),
                    enabled = !busy,
                    quiet = true,
                    onClick = {
                        scope.launch {
                            store.remove()
                            container.speaker.clearCache()
                            note = null
                            refresh()
                        }
                    }
                )
            }

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
}
