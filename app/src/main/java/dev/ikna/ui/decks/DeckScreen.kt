package dev.ikna.ui.decks

import dev.ikna.ui.text.S

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * One deck, and the things that are done to it once rather than every day.
 *
 * The on/off switch is deliberately NOT here. Switching decks is the one action
 * performed while comparing them — this one on, that one off — so it belongs on
 * the list where all of them are visible at once, and duplicating it here would
 * put two controls on one piece of state. The state is shown as a word instead.
 *
 * What lives here is everything that concerns a single deck and is done rarely:
 * the language, sending it to someone, and deleting it. Deleting is the only
 * irreversible action in the app, which is the main reason this is a screen and
 * not a sheet that can be flicked away half-confirmed.
 */
@Composable
fun DeckScreen(
    container: AppContainer,
    deckId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deck by remember { mutableStateOf<DeckSummary?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    // Deleting takes two taps on the same button rather than a dialog, the way
    // erasing everything already works in settings. A dialog asks a question the
    // second tap answers by itself.
    var armed by remember { mutableStateOf(false) }

    suspend fun reload() {
        deck = container.deckRepository.deck(deckId)
    }

    LaunchedEffect(deckId) { reload() }

    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val current = deck

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
                text = current?.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = Space.xs)
            )
        }

        if (current != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Edge)
            ) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = stateLine(current),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted
                )
                Spacer(Modifier.height(Space.md))
                IknaProgress(
                    fraction = if (current.total == 0) 0f
                    else current.introduced.toFloat() / current.total,
                    height = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    track = true
                )

                Spacer(Modifier.height(Space.xl))
                IknaRule()
                Spacer(Modifier.height(Space.lg))

                Text(
                    text = S.t("dp.003"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("dp.004"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(Space.md))

                // Four to a row instead of one strip that scrolls sideways: a
                // sideways strip hides half of its options behind a gesture
                // nobody is told about, and there are only eleven of them.
                LANGS.chunked(4).forEach { group ->
                    Row(
                        modifier = Modifier.padding(bottom = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        group.forEach { code ->
                            IknaChip(
                                label = if (code == NO_LANG) S.t("dp.005")
                                else code.uppercase(),
                                selected = current.lang == code,
                                onClick = {
                                    scope.launch {
                                        container.deckRepository.setLang(deckId, code)
                                        reload()
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Space.lg))
                IknaRule()
                Spacer(Modifier.height(Space.lg))

                IknaWideButton(
                    label = S.t("dp.006"),
                    onClick = {
                        scope.launch {
                            val body = container.deckRepository.exportText(deckId)
                            note = when {
                                body.isBlank() -> S.t("share.004")
                                !DeckShare.shareText(
                                    context = context,
                                    fileName = DeckShare.fileNameFor(current.title),
                                    body = body,
                                    chooserTitle = S.t("share.002")
                                ) -> S.t("share.003")
                                else -> null
                            }
                        }
                    }
                )

                // Deleting is put well below everything else, behind its own
                // line, and says what survives before it says what goes: the
                // answers stay in the log either way, because that table is the
                // one thing in the app that is never rewritten.
                Spacer(Modifier.height(Space.xxl))
                IknaRule()
                Spacer(Modifier.height(Space.lg))

                Text(
                    text = S.t("dp.009"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(Space.sm))
                IknaTextButton(
                    label = if (armed) S.t("dp.008") else S.t("dp.007"),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = {
                        if (!armed) {
                            armed = true
                        } else {
                            scope.launch {
                                container.deckRepository.delete(deckId)
                                // The day was planned while this deck still
                                // existed, so the plan is dropped rather than
                                // left pointing at cards that are gone.
                                container.learningRepository.invalidatePlan()
                                onBack()
                            }
                        }
                    }
                )

                note?.let { text ->
                    Spacer(Modifier.height(Space.lg))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }

                Spacer(Modifier.height(Space.xxl))
            }
        }
    }
}

/** State, position in the deck, and nothing else: "Включена · 34 / 121 · 28%". */
private fun stateLine(deck: DeckSummary): String {
    val state = if (deck.isActive) S.t("dp.001") else S.t("dp.002")
    val percent = if (deck.total <= 0) 0 else deck.introduced * 100 / deck.total
    return state + " · " + deck.introduced + " / " + deck.total + " · " + percent + "%"
}

/**
 * What an imported deck starts as: no language claimed, so no voice is offered
 * rather than the wrong voice reading the phrase in someone else's accent.
 */
private const val NO_LANG = "custom"

private val LANGS = listOf(
    "en", "pl", "ru", "es",
    "fr", "de", "it", "pt",
    "zh", "ja", NO_LANG
)
