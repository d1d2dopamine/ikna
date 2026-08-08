package dev.ikna.ui.decks

import dev.ikna.ui.text.S

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.Space
import dev.ikna.widget.TodayWidget
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
            message = if (result.installed == 0) S.t("deck.001")
            else S.t("deck.002") + result.installed +
                (if (result.skipped > 0) S.t("deck.003") + result.skipped else "")
        }
    }

    val todayTotal = today.values.sum()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // The number on the home screen widget comes from here. A widget cannot read
    // the database from the launcher's process, so the app hands it the finished
    // text every time this screen knows a new value - and this screen is the one
    // a session returns to, so it always does.
    LaunchedEffect(todayTotal, S.lang) {
        TodayWidget.publish(
            context = context,
            count = todayTotal,
            title = S.t("deck.007"),
            label = cardWord(todayTotal)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The name of the app, and nothing else up here. The marks that used to
        // share this row now live in the bar at the bottom of the screen: a phone
        // is held low in one hand, and the top of the screen is the one place a
        // thumb cannot go without regripping the device.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .padding(start = Edge, end = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = S.t("deck.004"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(modifier = Modifier.padding(horizontal = Edge)) {
            Spacer(Modifier.height(Space.md))
            TodayBlock(total = todayTotal, onClick = { onOpenSession(null) })
            Spacer(Modifier.height(Space.xl))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Edge),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
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
                        text = S.t("deck.005"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = Edge)) {
            val note = when {
                busy -> S.t("deck.006")
                message != null -> message
                else -> null
            }
            if (note != null) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted
                )
            }
            Spacer(Modifier.height(Space.md))
        }

        // Everything that is not learning, as marks rather than as a slab of
        // words, in the corner the hand already rests in. Adding a deck is the
        // rarest action in the app and used to be a full-width button.
        IknaBottomBar {
            IknaIconButton(
                glyph = IknaGlyph.BARS,
                onClick = onOpenStats,
                label = S.t("a11y.003")
            )
            IknaIconButton(
                glyph = IknaGlyph.GEAR,
                onClick = onOpenSettings,
                label = S.t("a11y.002")
            )
            Spacer(Modifier.weight(1f))
            IknaIconButton(
                glyph = IknaGlyph.PLUS,
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !busy,
                label = S.t("a11y.004")
            )
        }
    }
}

/**
 * Everything due today, as a number and nothing else.
 *
 * This was a bordered box with a heading inside it — a control, competing for
 * attention with every other bordered box below. It is not a control. It is the
 * answer to the only question the screen is asked, so it is set at display size
 * in the accent colour and given room, and the frame is gone. The eye lands on
 * the number before it has read a single word, which is the whole job.
 *
 * On a finished day it drops to the muted colour and stops being clickable. No
 * congratulation, no badge, no streak: the reward for finishing is that the
 * screen goes quiet.
 */
@Composable
private fun TodayBlock(total: Int, onClick: () -> Unit) {
    val enabled = total > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.sm)
    ) {
        Text(
            text = S.t("deck.007"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.sm))
        if (enabled) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    text = cardWord(total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "\u2192",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alignByBaseline()
                )
            }
        } else {
            Text(
                text = S.t("deck.008"),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One deck: its mark, what it owes today, and how far through it you are.
 *
 * The outline is gone. A list of identical rectangles is read as a list of
 * identical rectangles — nothing in it is faster to find than anything else, so
 * the eye has to read every title in order. The mark on the left fixes that: two
 * large letters, filled with the accent when the deck owes work and hollow when
 * it does not, so "which deck do I owe today" is answered by colour and shape
 * before any reading happens. That is the whole reason this screen exists.
 *
 * Rows are separated by space rather than by lines. With a solid mark anchoring
 * each row, a border adds nothing except another rectangle.
 */
@Composable
private fun DeckRow(
    deck: DeckSummary,
    dueToday: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val owes = dueToday > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = owes, onClick = onOpen),
        verticalAlignment = Alignment.Top
    ) {
        DeckMark(deck = deck, owes = owes)
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground.copy(
                            alpha = if (deck.isActive) 1f else 0.55f
                        )
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = if (owes) S.t("deck.009") + dueToday else S.t("deck.010"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (owes) accent else muted
                    )
                }
                IknaToggle(
                    checked = deck.isActive,
                    onCheckedChange = onToggle,
                    label = S.t("a11y.006")
                )
            }
            if (deck.isActive) {
                Spacer(Modifier.height(Space.md))
                IknaProgress(
                    fraction = if (deck.total == 0) 0f else deck.introduced.toFloat() / deck.total,
                    height = 2.dp,
                    color = if (owes) accent else muted,
                    // Here the empty part means something — it is the rest of the deck.
                    track = true
                )
                Spacer(Modifier.height(Space.sm))
                // One number under the bar, not three.
                //
                // This line used to read "введено 34 из 121 · знаю 12": three
                // figures in a sentence, under a bar that already draws the first
                // two of them. Nobody reads a three-number sentence on a list row
                // — the eye slides off it — and the two numbers it repeated were
                // the two the bar was for. What is left is how far through the
                // deck you are, as a percentage, which is the only part the bar
                // cannot say out loud.
                Text(
                    text = percentDone(deck.introduced, deck.total),
                    style = MaterialTheme.typography.labelMedium,
                    color = muted
                )
            }
        }
    }
}

/**
 * Two letters in a square, and the square is the whole signal.
 *
 * Filled means this deck wants something from you today. Hollow means it is done
 * or resting. Faint means it is switched off. Three states, no words, readable
 * across a room — and the letters come from the deck's language, so PL and EN
 * stay in the same place on the screen every day and become landmarks instead of
 * labels.
 */
@Composable
private fun DeckMark(deck: DeckSummary, owes: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.background

    val fill = if (owes) accent else background
    val ink = when {
        owes -> background
        deck.isActive -> MaterialTheme.colorScheme.onBackground
        else -> muted.copy(alpha = 0.6f)
    }
    val edge = when {
        owes -> accent
        deck.isActive -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .background(fill)
            .border(Space.hair, edge),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monogramOf(deck.lang, deck.title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1
        )
    }
}

/** How far into the deck, as the one figure the progress bar cannot state. */
private fun percentDone(introduced: Int, total: Int): String {
    if (total <= 0) return "0%"
    return (introduced * 100 / total).toString() + "%"
}

private fun cardWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> S.t("deck.014")
        mod10 == 1 -> S.t("deck.015")
        mod10 in 2..4 -> S.t("deck.016")
        else -> S.t("deck.017")
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
