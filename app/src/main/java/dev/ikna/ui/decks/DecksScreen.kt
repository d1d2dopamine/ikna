package dev.ikna.ui.decks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import dev.ikna.ui.theme.IknaGood
import dev.ikna.ui.theme.IknaMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decks.
 *
 * Switching a deck off stops new chunks from it and nothing else — started
 * cards keep their schedule. So this screen has no destructive choice on it,
 * which is deliberate: a screen where every switch is reversible is a screen
 * you can poke at without deciding anything.
 */
@Composable
fun DecksScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        decks = container.deckRepository.decks()
    }

    LaunchedEffect(Unit) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                val name = displayName(context, uri)
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                container.deckRepository.importFile(name, text)
            }
            // The user just asked for this content, so let it into today's plan
            // instead of making them wait until tomorrow.
            container.learningRepository.invalidatePlan()
            reload()
            busy = false
            message = if (result.installed == 0) "Не получилось прочитать ни одной строки"
            else "Добавлено чанков: " + result.installed +
                (if (result.skipped > 0) ", пропущено " + result.skipped else "")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Наборы",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Выключенный набор просто не даёт новых чанков. Уже начатые карточки остаются со своими сроками.",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(decks, key = { it.id }) { deck ->
                DeckRow(deck) { active ->
                    scope.launch {
                        container.deckRepository.setActive(deck.id, active)
                        reload()
                    }
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = IknaMuted
            )
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Читаю файл…" else "Добавить свой набор (.jsonl)") }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Формат тот же, что у встроенного набора: одна строка — один чанк.",
            style = MaterialTheme.typography.bodySmall,
            color = IknaMuted
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DeckRow(deck: DeckSummary, onToggle: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "введено " + deck.introduced + " из " + deck.total +
                            " · знаешь " + deck.known,
                        style = MaterialTheme.typography.bodySmall,
                        color = IknaMuted
                    )
                }
                Switch(checked = deck.isActive, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (deck.total == 0) 0f else deck.introduced.toFloat() / deck.total },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = IknaGood,
                trackColor = IknaMuted.copy(alpha = 0.22f)
            )
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            cursor.getString(index)?.let { return it }
        }
    }
    return uri.lastPathSegment ?: "pack.jsonl"
}
