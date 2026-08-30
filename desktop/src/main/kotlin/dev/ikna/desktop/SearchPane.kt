package dev.ikna.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.data.db.ChunkSearchRow
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextField
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search across the decks already installed on this machine.
 *
 * The phone has this screen and the window did not, so a phrase you half
 * remembered was unfindable here: you knew it was in some pack, and there was
 * nothing to ask. Deliberately local -- no network, no catalogue, only the
 * chunks in this machine own database -- so the answer is always the truth
 * about what is actually here.
 */
@Composable
fun SearchPane(
    container: DesktopContainer,
    palette: IknaPalette,
    decks: List<DeckSummary>,
    onOpenDeck: (String) -> Unit,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ChunkSearchRow>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var asked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run() {
        val raw = query.trim()
        if (raw.length < 2) {
            rows = emptyList()
            asked = true
            message = S.t("search.006")
            return
        }
        busy = true
        message = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { container.deckRepository.search(raw, limit = 80) }
            }
            busy = false
            asked = true
            outcome
                .onSuccess { found ->
                    rows = found
                    message = when {
                        found.isEmpty() -> S.t("search.007")
                        found.size >= 80 -> S.t("search.008")
                        else -> null
                    }
                }
                .onFailure { error ->
                    rows = emptyList()
                    message = S.t("search.009")
                    logLine("search failed: " + error)
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("search.001")
            )
        }
        Spacer(Modifier.height(Space.md))
        Column(modifier = Modifier.widthIn(max = 720.dp)) {
            SectionTitle(S.t("search.001"), palette)
            Spacer(Modifier.height(Space.sm))
            Text(
                text = S.t("search.002"),
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
            Spacer(Modifier.height(Space.lg))
            IknaTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = S.t("search.003"),
                onSearch = { run() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Space.md))
            IknaWideButton(
                label = if (busy) S.t("search.005") else S.t("search.004"),
                onClick = { run() },
                modifier = Modifier.widthIn(max = 260.dp),
                filled = true,
                enabled = !busy
            )
            Spacer(Modifier.height(Space.lg))

            val note = message
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )
                Spacer(Modifier.height(Space.md))
            }

            if (!asked && !busy) {
                Column(modifier = Modifier.widthIn(max = 320.dp)) {
                    IknaLatticePlaceholder()
                }
            }

            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    Spacer(Modifier.height(Space.md))
                    IknaRule(color = palette.line)
                    Spacer(Modifier.height(Space.md))
                }
                ResultRow(
                    row = row,
                    palette = palette,
                    deckTitle = decks.firstOrNull { it.id == row.packId }?.title ?: row.packTitle,
                    onOpen = { onOpenDeck(row.packId) }
                )
            }
        }
    }
}

/** One chunk: the phrase, the sentence it lives in, its meaning, its deck. */
@Composable
private fun ResultRow(
    row: ChunkSearchRow,
    palette: IknaPalette,
    deckTitle: String,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = Space.xs)
    ) {
        Text(
            text = row.text,
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink
        )
        val sentence = row.contextSentence
        if (!sentence.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }
        val meaning = row.translation
        if (!meaning.isNullOrBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink
            )
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            text = S.t("search.010") + deckTitle,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted
        )
    }
}
