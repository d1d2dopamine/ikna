package dev.ikna.ui.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.ikna.AppContainer
import dev.ikna.data.catalog.catalogMeaning
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.db.ChunkSearchRow
import dev.ikna.data.repo.localSearchTerms
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.launch

/** Local concordance over content already installed on the phone. */
@Composable
fun DeckSearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChunkSearchRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var token by remember { mutableIntStateOf(0) }

    fun search() {
        if (localSearchTerms(query) == null) {
            token++
            results = emptyList()
            searched = false
            failed = false
            loading = false
            return
        }
        val request = token + 1
        val requestedQuery = query
        token = request
        loading = true
        searched = true
        failed = false
        scope.launch {
            val found = runCatching { container.deckRepository.search(requestedQuery) }
            if (token != request) return@launch
            results = found.getOrDefault(emptyList())
            failed = found.isFailure
            loading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(bottom = BarHeight)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BarHeight)
                    .padding(horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = S.t("search.001"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Space.xs)
                )
            }

            Column(modifier = Modifier.padding(horizontal = Edge)) {
                Spacer(Modifier.height(Space.md))
                Text(
                    text = S.t("search.002"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.md))
                IknaTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        // Invalidate a Room query for the previous text. Room can
                        // cancel suspend queries, and the token also prevents a
                        // late result from being drawn under a newer query.
                        token++
                        loading = false
                        searched = false
                        failed = false
                        results = emptyList()
                    },
                    placeholder = S.t("search.003"),
                    onSearch = ::search,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.sm))
                IknaWideButton(
                    label = if (loading) S.t("search.005") else S.t("search.004"),
                    filled = true,
                    enabled = !loading && localSearchTerms(query) != null,
                    onClick = ::search
                )
                Spacer(Modifier.height(Space.lg))
                IknaRule()
                Spacer(Modifier.height(Space.md))
            }

            when {
                failed -> SearchMessage(S.t("search.009"))
                loading -> SearchMessage(S.t("search.005"))
                !searched -> SearchMessage(S.t("search.006"), lattice = true)
                results.isEmpty() -> SearchMessage(S.t("search.007"), lattice = true)
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Edge)
                ) {
                    items(results, key = { it.chunkId }) { row ->
                        SearchResult(
                            row = row,
                            onOpenDeck = { onOpenDeck(row.packId) },
                            onSource = { id -> openTatoeba(context, id) }
                        )
                        Spacer(Modifier.height(Space.md))
                        IknaRule()
                        Spacer(Modifier.height(Space.md))
                    }
                    if (results.size == 80) {
                        item {
                            Text(
                                text = S.t("search.008"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(Space.xl))
                        }
                    }
                }
            }
        }
        IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
            IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}

@Composable
private fun SearchMessage(text: String, lattice: Boolean = false) {
    Column(modifier = Modifier.padding(horizontal = Edge, vertical = Space.md)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lattice) {
            Spacer(Modifier.height(Space.xl))
            IknaLatticePlaceholder()
        }
    }
}

@Composable
private fun SearchResult(
    row: ChunkSearchRow,
    onOpenDeck: () -> Unit,
    onSource: (String) -> Unit
) {
    val meaning = catalogMeaning(row.translation)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = row.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = row.contextSentence,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = meaning.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        meaning.tatoebaId?.let { id ->
            Spacer(Modifier.height(Space.xs))
            Text(
                text = S.t("src.001") + "Tatoeba #" + id,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onSource(id) }
            )
        }
        Spacer(Modifier.height(Space.sm))
        IknaTextButton(
            label = S.t("search.010") + row.packTitle,
            onClick = onOpenDeck,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openTatoeba(context: Context, id: String) {
    val url = tatoebaSentenceUrl(id) ?: return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
