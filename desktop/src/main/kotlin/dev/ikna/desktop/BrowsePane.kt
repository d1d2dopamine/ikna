package dev.ikna.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.domain.session.BrowsePlan
import dev.ikna.ui.session.BrowseChunkCard
import dev.ikna.ui.session.BrowseableCard
import dev.ikna.ui.session.IknaBrowseEmptyState
import dev.ikna.ui.session.IknaBrowseTopBar
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaPalette
import kotlinx.coroutines.launch

/** Desktop host for the same neutral Browse card used on Android. */
@Composable
fun BrowsePane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String,
    onChanged: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var plan by remember(deckId) { mutableStateOf<BrowsePlan?>(null) }
    var index by remember(deckId) { mutableStateOf(0) }
    var loading by remember(deckId) { mutableStateOf(true) }
    var saving by remember(deckId) { mutableStateOf(false) }
    var finished by remember(deckId) { mutableStateOf(false) }

    LaunchedEffect(deckId) {
        loading = true
        val opened = runCatching { container.learningRepository.startBrowse(deckId) }
            .getOrNull()
        plan = opened
        index = 0
        finished = opened?.cards.isNullOrEmpty()
        loading = false
        if (!opened?.cards.isNullOrEmpty()) onChanged()
        runCatching { focus.requestFocus() }
    }

    val cards = plan?.cards.orEmpty()
    val current = if (finished) null else cards.getOrNull(index)

    val advance: () -> Unit = {
        if (!loading && !saving && !finished) {
            val nextIndex = index + 1
            val next = cards.getOrNull(nextIndex)
            if (next == null) {
                finished = true
                onChanged()
            } else {
                saving = true
                scope.launch {
                    val recorded = runCatching {
                        container.learningRepository.recordBrowse(next, deckId)
                    }.getOrDefault(false)
                    if (recorded) index = nextIndex else finished = true
                    saving = false
                    onChanged()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (loading || saving || event.type != KeyEventType.KeyDown || current == null) {
                    false
                } else when (event.key) {
                    Key.DirectionRight, Key.Spacebar, Key.Enter -> {
                        advance()
                        true
                    }
                    else -> false
                }
            }
    ) {
        IknaBrowseTopBar(plan?.deckTitle.orEmpty())
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )

                saving -> Box(Modifier.fillMaxSize())

                current != null -> {
                    val swipeLine = with(LocalDensity.current) {
                        (maxWidth.toPx() * 0.13f).coerceIn(56f, 220f)
                    }
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        BrowseableCard(
                            key = current.card.key + ":" + index,
                            animations = settings.animations,
                            threshold = swipeLine,
                            onNext = advance
                        ) {
                            BrowseChunkCard(
                                card = current,
                                transcription = Phonetics.line(
                                    ipa = current.promptIpa,
                                    lang = current.chunk.lang,
                                    mode = settings.phoneticsFor(current.chunk.packId)
                                ),
                                sourceLabel = current.sourceId?.let {
                                    S.t("src.001") + "Tatoeba #" + it
                                },
                                onSource = current.sourceId?.let { id ->
                                    { tatoebaSentenceUrl(id)?.let(::openInBrowser); Unit }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                else -> IknaBrowseEmptyState(
                    hadCards = cards.isNotEmpty(),
                    animations = settings.animations
                )
            }
        }

        IknaBottomBar {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("a11y.001")
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
