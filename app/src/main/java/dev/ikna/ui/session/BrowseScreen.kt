package dev.ikna.ui.session

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ikna.AppContainer
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.domain.phonetics.Phonetics
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.flow.distinctUntilChanged

/** Passive reading feed after the required daily plan, without ratings or swipes. */
@Composable
fun BrowseScreen(
    container: AppContainer,
    deckId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: BrowseViewModel = viewModel(
        key = "browse:$deckId",
        factory = BrowseViewModel.factory(container.learningRepository, deckId)
    )
    val state by vm.state.collectAsState()
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())
    val listState = rememberLazyListState()
    var developerBlockers by remember(deckId) { mutableStateOf<String?>(null) }

    LaunchedEffect(deckId) {
        developerBlockers = if (container.isDeveloperMode) {
            val availability = runCatching {
                container.learningRepository.browseDeckAvailability(listOf(deckId))[deckId]
            }.getOrNull()
            availability?.takeIf { it.forcedByDeveloper && it.blockers.isNotEmpty() }
                ?.let { S.t("dev.001") + " · " + browseUnavailableText(it) }
        } else null
    }

    LaunchedEffect(listState, state.queue) {
        if (state.queue.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            browseMeaningfullyVisibleIndices(
                viewportStart = layout.viewportStartOffset,
                viewportEnd = layout.viewportEndOffset,
                items = layout.visibleItemsInfo.map { item ->
                    BrowseViewportItem(item.index, item.offset, item.size)
                }
            )
        }.distinctUntilChanged().collect { indices -> vm.recordVisible(indices) }
    }

    val view = LocalView.current
    val keepScreenAwake = state.queue.isNotEmpty() && !state.loading
    DisposableEffect(view, keepScreenAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = previous || keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IknaBrowseTopBar(deckTitle = state.deckTitle)
        developerBlockers?.let { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = Space.xs)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> Text(
                    text = S.t("sess.001"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.queue.isEmpty() -> IknaBrowseEmptyState()

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.lg)
                ) {
                    itemsIndexed(
                        items = state.queue,
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
                                sourceLabel = card.sourceId?.let { S.t("src.001") + "Tatoeba #" + it },
                                onSource = card.sourceId?.let { id ->
                                    { openBrowseSource(context, id) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 960.dp)
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

private fun openBrowseSource(context: Context, id: String) {
    val url = tatoebaSentenceUrl(id) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
