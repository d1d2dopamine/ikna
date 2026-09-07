package dev.ikna.desktop
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import dev.ikna.data.db.ChunkSearchRow
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.repo.localSearchTerms
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.ui.search.IknaSearchResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*

@Composable
fun SearchPane(container: DesktopContainer, palette: IknaPalette, decks: List<DeckSummary>,
    onOpenDeck: (String) -> Unit, onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ChunkSearchRow>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(0) }
    fun search() {
        if (localSearchTerms(query) == null) return
        val request = ++token
        val requestedQuery = query
        busy = true; searched = true; failed = false
        scope.launch {
            val result = runCatching { container.deckRepository.search(requestedQuery, limit = 80) }
            if (token != request) return@launch
            rows = result.getOrDefault(emptyList()); failed = result.isFailure; busy = false
        }
    }
    DesktopPaneFrame(S.t("search.001"), onBack) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
            Spacer(Modifier.height(Space.md))
            Text(S.t("search.002"), style = MaterialTheme.typography.bodyMedium, color = palette.muted)
            Spacer(Modifier.height(Space.md))
            Box(Modifier.widthIn(max = 760.dp).fillMaxWidth().border(Space.hair, palette.line).padding(Space.md)) {
                BasicTextField(query, onValueChange = {
                    query = it; token++; rows = emptyList(); searched = false; busy = false; failed = false
                }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = palette.ink),
                    cursorBrush = SolidColor(palette.ink), modifier = Modifier.fillMaxWidth().onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyDown && (it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                            search(); true
                        } else false
                    }, decorationBox = { field ->
                        if (query.isEmpty()) Text(S.t("search.003"), style = MaterialTheme.typography.bodyLarge, color = palette.muted)
                        field()
                    })
            }
            Spacer(Modifier.height(Space.sm))
            IknaWideButton(if (busy) S.t("search.005") else S.t("search.004"), onClick = ::search,
                modifier = Modifier.widthIn(max = 320.dp), filled = true,
                enabled = !busy && localSearchTerms(query) != null)
            Spacer(Modifier.height(Space.lg)); IknaRule(); Spacer(Modifier.height(Space.md))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp)) {
            if (busy || failed || !searched || rows.isEmpty()) item {
                Text(S.t(when { failed -> "search.009"; busy -> "search.005"; !searched -> "search.006"; else -> "search.007" }),
                    style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                if (!busy && !failed) { Spacer(Modifier.height(Space.xl)); IknaLatticePlaceholder() }
            }
            items(rows, key = { it.chunkId }) { row ->
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    IknaSearchResult(row, onOpenDeck = { onOpenDeck(row.packId) },
                        onSource = { id -> tatoebaSentenceUrl(id)?.let(::openInBrowser) })
                    Spacer(Modifier.height(Space.md)); IknaRule(); Spacer(Modifier.height(Space.md))
                }
            }
            if (rows.size == 80) item { Text(S.t("search.008"), style = MaterialTheme.typography.bodySmall, color = palette.muted) }
        }
    }
}
