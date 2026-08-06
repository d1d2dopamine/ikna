package dev.ikna.ui.decks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The first screen: decks, and how much each of them owes today.
 *
 * This used to be a side tab behind an edge-swipe drawer, while the app opened
 * straight into a session. That made sense when there was one deck. It stops
 * making sense the moment two languages are being learned in parallel, because
 * then "start" is a question with more than one answer and the app was answering
 * it silently.
 *
 * The counts come from one plan for the whole day, filtered per deck — not from
 * a plan per deck. Two decks are two pools drawn from the same budget, never
 * twice the work.
 *
 * Switching a deck off stops new chunks from it and nothing else: started cards
 * keep their schedule. Nothing on this screen destroys anything, which is why it
 * is safe to poke at.
 */
@Composable
fun DecksScreen(
    container: AppContainer,
    onOpenSession: (String?) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var today by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    suspend fun reload() {
        decks = container.deckRepository.decks()
        // The home screen must survive a bad plan. If the day cannot be built for
        // any reason, the decks still list — without today's numbers.
        today = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
    }

    // Re-runs whenever this screen comes back to the front, so the counts are
    // right after a session instead of a minute stale.
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

    val todayTotal = today.values.sum()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = Modifier.fillMaxSize()) {
        // Everything that is not learning lives in this one row, as marks rather
        // than as a slab of words. Adding a deck is the rarest action in the app
        // and used to be a full-width button pinned to the bottom of the screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Колоды",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IknaIconButton(
                glyph = IknaGlyph.PLUS,
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !busy
            )
            IknaIconButton(glyph = IknaGlyph.BARS, onClick = onOpenStats)
            IknaIconButton(glyph = IknaGlyph.GEAR, onClick = onOpenSettings)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            TodayRow(total = todayTotal, onClick = { onOpenSession(null) })
            Spacer(Modifier.height(18.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(decks, key = { it.id }) { deck ->
                DeckRow(
                    deck = deck,
                    dueToday = today[deck.id] ?: 0,
                    onOpen = { onOpenSession(deck.id) },
                    onToggle = { active ->
                        scope.launch {
                            container.deckRepository.setActive(deck.id, active)
                            reload()
                        }
                    }
                )
            }
            if (decks.isEmpty()) {
                item {
                    Text(
                        text = "Пока ни одной колоды. Плюс сверху добавит файл .jsonl.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            val note = when {
                busy -> "читаю файл…"
                message != null -> message
                else -> null
            }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/**
 * Everything due today, in one row, at the top.
 *
 * Mixed learning means most days start here rather than in a particular deck:
 * the schedule already knows what is owed and in what order, and picking a deck
 * by hand is the exception, not the default.
 */
@Composable
private fun TodayRow(total: Int, onClick: () -> Unit) {
    val enabled = total > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (enabled) 2.dp else 1.dp,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "СЕГОДНЯ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (enabled) total.toString() + " " + cardWord(total)
                else "ничего не ждёт",
                style = MaterialTheme.typography.headlineMedium,
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) {
            Text(
                text = "→",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(2.dp))
        }
    }
}

/**
 * One deck: what it owes today, and how far through it you are.
 *
 * An outline and nothing else. It used to be a Material Card, which fills itself
 * with the surface colour and sits on the background like a tile — the same
 * "panel floating on a screen" look the session card had, in a place where the
 * only job is to separate one row from the next.
 */
@Composable
private fun DeckRow(
    deck: DeckSummary,
    dueToday: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .clickable(enabled = dueToday > 0, onClick = onOpen)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deck.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (dueToday > 0) "сегодня " + dueToday else "на сегодня нет",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (dueToday > 0) MaterialTheme.colorScheme.primary else muted
                )
            }
            IknaToggle(checked = deck.isActive, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(14.dp))
        IknaProgress(
            fraction = if (deck.total == 0) 0f else deck.introduced.toFloat() / deck.total,
            height = 4.dp,
            color = MaterialTheme.colorScheme.primary,
            // Here the empty part means something — it is the rest of the deck.
            track = true
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "введено " + deck.introduced + " из " + deck.total + " · знаешь " + deck.known,
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )
    }
}

private fun cardWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "карточек"
        mod10 == 1 -> "карточка"
        mod10 in 2..4 -> "карточки"
        else -> "карточек"
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
