package dev.ikna.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.domain.session.BrowsePlan
import dev.ikna.ui.session.BrowseFeedCard
import dev.ikna.ui.session.BrowseViewportItem
import dev.ikna.ui.session.IknaBrowseEmptyState
import dev.ikna.ui.session.IknaBrowseTopBar
import dev.ikna.ui.session.browseMeaningfullyVisibleIndices
import dev.ikna.ui.session.browseUnavailableText
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged

/** Desktop host for the passive vertical Browse feed. */
@Composable
fun BrowsePane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String,
    onChanged: () -> Unit,
    onBack: () -> Unit
) {
    var plan by remember(deckId) { mutableStateOf<BrowsePlan?>(null) }
    var loading by remember(deckId) { mutableStateOf(true) }
    var developerBlockers by remember(deckId) { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val exposureAttempts = remember(deckId) { linkedSetOf<String>() }

    LaunchedEffect(deckId) {
        developerBlockers = if (container.isDeveloperMode) {
            val availability = runCatching {
                container.learningRepository.browseDeckAvailability(listOf(deckId))[deckId]
            }.getOrNull()
            availability?.takeIf { it.forcedByDeveloper && it.blockers.isNotEmpty() }
                ?.let { S.t("dev.001") + " · " + browseUnavailableText(it) }
        } else null
    }

    LaunchedEffect(deckId) {
        loading = true
        plan = runCatching { container.learningRepository.startBrowse(deckId) }.getOrNull()
        loading = false
    }

    val cards = plan?.cards.orEmpty()
    LaunchedEffect(listState, cards) {
        if (cards.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            browseMeaningfullyVisibleIndices(
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
                items = layout.visibleItemsInfo.map { item ->
                    BrowseViewportItem(item.index, item.offset, item.size)
                }
            )
        }.distinctUntilChanged().collect { indices ->
            var changed = false
            for (index in indices) {
                val card = cards.getOrNull(index) ?: continue
                if (!exposureAttempts.add(card.card.key)) continue
                val recorded = runCatching {
                    container.learningRepository.recordBrowse(card, deckId)
                }.getOrDefault(false)
                changed = changed || recorded
            }
            if (changed) onChanged()
        }
    }

    Column(Modifier.fillMaxSize()) {
        IknaBrowseTopBar(plan?.deckTitle.orEmpty())
        developerBlockers?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = Space.xs)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )

                cards.isEmpty() -> IknaBrowseEmptyState()

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    itemsIndexed(
                        items = cards,
                        key = { _, card -> card.card.key }
                    ) { _, card ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            BrowseFeedCard(
                                card = card,
                                transcription = Phonetics.line(
                                    ipa = card.promptIpa,
                                    lang = card.chunk.lang,
                                    mode = settings.phoneticsFor(card.chunk.packId)
                                ),
                                sourceLabel = card.sourceId?.let {
                                    S.t("src.001") + "Tatoeba #" + it
                                },
                                onSource = card.sourceId?.let { id ->
                                    { tatoebaSentenceUrl(id)?.let(::openInBrowser); Unit }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 640.dp)
                            )
                        }
                    }
                }
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
