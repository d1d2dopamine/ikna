package dev.ikna.ui.decks

import dev.ikna.ui.text.S

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.NO_TINT
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.DeckTints
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.deckTintColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    settings: IknaSettings,
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

    // Adding cards to a deck that already exists: this button, then the file.
    // There was no way to do it at all -- every import made a deck, so a course
    // arriving in portions turned into five decks with the same name.
    var adding by remember { mutableStateOf(false) }
    val more = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val size = sizeOf(context, uri)
            if (size != null && size > MAX_FILE_BYTES) {
                note = S.t("add.018")
                return@launch
            }
            adding = true
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            val report = if (read == null) null else withContext(Dispatchers.IO) {
                runCatching {
                    container.deckRepository.importText(
                        fileName = displayName(context, uri),
                        text = read,
                        fallbackTitle = S.t("deckrepo.001"),
                        appendTo = deckId
                    )
                }.getOrNull()
            }
            adding = false
            if (report != null && report.installed > 0) {
                // The day was planned before these cards existed.
                container.learningRepository.invalidatePlan()
                reload()
            }
            note = if (report == null) S.t("add.019") else describe(report)
        }
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

                LangChips(
                    current = current.lang,
                    onPick = { code ->
                        scope.launch {
                            container.deckRepository.setLang(deckId, code)
                            reload()
                        }
                    }
                )

                Spacer(Modifier.height(Space.lg))
                IknaRule()
                Spacer(Modifier.height(Space.lg))

                // What the deck looks like on the list.
                //
                // Kept out of the deck file on purpose. An icon and a colour are
                // how one person tells their own decks apart at a glance; they
                // are not part of what the deck teaches, and a deck sent to
                // somebody else should arrive as cards rather than as somebody
                // else's taste. So this is stored in settings, beside the theme.
                val look = settings.lookFor(deckId)

                Text(
                    text = S.t("look.001"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("look.002"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(Space.md))

                // Two characters, typed, rather than a grid of emoji.
                //
                // The grid was here until 0.2.0 and it was the wrong offer: the
                // phone draws emoji in its own full-colour style, which sits on
                // this app's flat marks like a sticker on a blueprint. A field
                // is also smaller than the grid it replaces, and it accepts the
                // things people actually want in that square -- initials, a
                // language pair, a number -- none of which a fixed set of
                // pictures could have guessed.
                //
                // Left empty, the square keeps working out its own letters, so
                // there is nothing to undo and no third state to explain.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(Space.hair, MaterialTheme.colorScheme.outline),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = look.label,
                            onValueChange = { typed ->
                                scope.launch {
                                    container.settings.setDeckLook(
                                        packId = deckId,
                                        label = typed,
                                        tint = look.tint
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        text = S.t("look.004"),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted
                    )
                }

                Spacer(Modifier.height(Space.sm))
                Text(
                    text = S.t("look.003"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
                Spacer(Modifier.height(Space.sm))

                // Eight fixed colours, no colour picker. The square has to stay
                // legible against nine palettes in two lighting modes, and a
                // free hex field is one slider away from a deck nobody can see.
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    DeckTints.forEachIndexed { index, colour ->
                        val picked = look.tint == index
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(colour)
                                .border(
                                    if (picked) 2.dp else Space.hair,
                                    if (picked) MaterialTheme.colorScheme.onBackground
                                    else MaterialTheme.colorScheme.outline
                                )
                                .clickable {
                                    scope.launch {
                                        container.settings.setDeckLook(
                                            packId = deckId,
                                            label = look.label,
                                            tint = if (picked) NO_TINT else index
                                        )
                                    }
                                }
                        )
                    }
                }

                Spacer(Modifier.height(Space.lg))
                IknaRule()
                Spacer(Modifier.height(Space.lg))

                IknaWideButton(
                    label = if (adding) S.t("dp.011") else S.t("dp.010"),
                    enabled = !adding,
                    onClick = { more.launch(ACCEPTED_TYPES) }
                )
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = S.t("dp.012"),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )

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
