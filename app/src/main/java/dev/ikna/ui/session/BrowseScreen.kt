package dev.ikna.ui.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

/** Optional reading after the required daily plan, without ratings or reviews. */
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
    val card = state.current

    val view = LocalView.current
    val keepScreenAwake = card != null && !state.loading && !state.finished
    DisposableEffect(view, keepScreenAwake) {
        val previous = view.keepScreenOn
        view.keepScreenOn = previous || keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IknaBrowseTopBar(deckTitle = state.deckTitle)
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

                state.advancing -> Box(Modifier.fillMaxSize())

                card != null -> BrowseableCard(
                    key = card.card.key + ":" + state.index,
                    animations = settings.animations,
                    onNext = vm::next
                ) {
                    BrowseChunkCard(
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
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> IknaBrowseEmptyState(
                    hadCards = state.hadCards,
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

private fun openBrowseSource(context: Context, id: String) {
    val url = tatoebaSentenceUrl(id) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
