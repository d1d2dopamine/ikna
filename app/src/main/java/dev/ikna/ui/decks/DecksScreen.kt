package dev.ikna.ui.decks

import dev.ikna.ui.text.S

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
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaToggle
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.deckTintColor
import dev.ikna.widget.TodayWidget
import kotlinx.coroutines.launch

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
    settings: IknaSettings,
    onOpenSession: (String?) -> Unit,
    onOpenDeck: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddDeck: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var today by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    suspend fun reload() {
        decks = container.deckRepository.decks()
        // The home screen must survive a bad plan. If the day cannot be built for
        // any reason, the decks still list — without today's numbers.
        today = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
    }

    // Re-runs whenever this screen comes back to the front, so the counts are
    // right after a session instead of a minute stale. Coming back from the
    // add-deck screen lands here too, which is how a deck imported a moment ago
    // is already in the list and already counted.
    LaunchedEffect(Unit) { reload() }

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

    // Only ever says that something did not work. A share that worked is
    // announced by the share sheet itself, and a line congratulating the user
    // for a thing they watched happen is noise.
    var note by remember { mutableStateOf<String?>(null) }

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
                    look = settings.lookFor(deck.id),
                    dueToday = today[deck.id] ?: 0,
                    perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
                    onOpen = { onOpenSession(deck.id) },
                    onOpenDeck = { onOpenDeck(deck.id) },
                    onToggle = { active ->
                        scope.launch {
                            container.deckRepository.setActive(deck.id, active)
                            // Switching a deck on or off changes what a day is
                            // made of, and the day has already been built and
                            // stored. Without dropping it, a deck turned on now
                            // owes nothing until tomorrow — which is how a deck
                            // could be enabled and then refuse to open. Importing
                            // a deck does exactly this, for exactly this reason;
                            // the toggle was the path that forgot to.
                            container.learningRepository.invalidatePlan()
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

        note?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.padding(horizontal = Edge, vertical = Space.sm)
            )
        }

        Spacer(Modifier.height(Space.md))

        // Everything that is not learning, as marks rather than as a slab of
        // words, in the corner the hand already rests in. Adding a deck is the
        // rarest action in the app and used to be a full-width button.
        IknaBottomBar {
            // The name of the thing, on the screen it opens on, in the corner the
            // hand is already in. Everywhere else the app is deliberately anonymous
            // — no header, no title bar, no logo over the cards — and that is worth
            // keeping, but it left the product without a single place where it says
            // what it is. One line of it here is enough.
            //
            // Not pressable. A logo that navigates is a logo pressed by accident,
            // and this one sits 12dp from the way into Progress and Settings. The
            // start padding is what lines the letters up with the glyphs beside
            // them: those are 20dp marks centred in 44dp targets, so their ink
            // begins 12dp inside the row and the wordmark has to begin there too.
            //
            // The square over the i is drawn in the accent, so the mark belongs to
            // whichever palette is on rather than to the one it was drawn in.
            //
            // Both of those are now a choice. The mark can be switched off in
            // settings, and the whole row can be mirrored for a left hand: the
            // marks are the only controls on this screen, a phone is held in
            // one hand, and until now every one of them sat on the far side of
            // it for half the people holding it. Mirrored, the rarest action
            // takes the corner the thumb rests in and the two everyday ones
            // stay together at the other end, which is the same layout read in
            // the other direction rather than a second design to maintain.
            if (settings.leftHanded) {
                IknaIconButton(
                    glyph = IknaGlyph.PLUS,
                    onClick = onAddDeck,
                    label = S.t("a11y.004")
                )
                Spacer(Modifier.weight(1f))
                IknaIconButton(
                    glyph = IknaGlyph.GEAR,
                    onClick = onOpenSettings,
                    label = S.t("a11y.002")
                )
                IknaIconButton(
                    glyph = IknaGlyph.BARS,
                    onClick = onOpenStats,
                    label = S.t("a11y.003")
                )
                if (settings.showWordmark) {
                    IknaWordmark(modifier = Modifier.padding(end = Space.md))
                }
            } else {
                if (settings.showWordmark) {
                    IknaWordmark(modifier = Modifier.padding(start = Space.md))
                }
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
                    onClick = onAddDeck,
                    label = S.t("a11y.004")
                )
            }
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
 * On a finished day it drops to the muted colour and the arrow goes away — but it
 * still opens. A dead control is not restraint, it is a screen that stopped
 * answering: "ничего не ждёт" is an answer, and the session screen already knows
 * how to hand a few extra cards to someone who asks anyway. No congratulation, no
 * badge, no streak: the reward for finishing is that the screen goes quiet.
 */
@Composable
private fun TodayBlock(total: Int, onClick: () -> Unit) {
    val enabled = total > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
    look: DeckLook,
    dueToday: Int,
    perCardMs: Long?,
    onOpen: () -> Unit,
    onOpenDeck: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val owes = dueToday > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Always openable, whatever the deck owes today.
            //
            // This row used to be clickable only when something was due, so a
            // deck with an empty plan could not be opened at all — tap, nothing,
            // no message, no reason given. Two ordinary situations landed there:
            // a deck just switched on (the day was built before it existed) and a
            // deck already finished today. Neither is a locked door. The session
            // screen has the empty state and the "a few more" path for exactly
            // this, and until now that path was unreachable.
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.Top
    ) {
        DeckMark(deck = deck, owes = owes, look = look)
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // The title is a title again.
                    //
                    // It used to be the only door to the deck's own screen, which
                    // meant the two destinations in this row were told apart by
                    // aiming at a word rather than at a control: nothing said the
                    // name was pressable, and the square on the left -- the one
                    // thing that looks like a button -- opened a session. Now the
                    // whole row starts the session, and the three dots below open
                    // the deck. Both are visible before they are touched.
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
                        text = if (owes) S.t("deck.009") + dueToday +
                            minutesTail(dueToday, perCardMs)
                        else S.t("deck.010"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (owes) accent else muted
                    )
                }
                IknaToggle(
                    checked = deck.isActive,
                    onCheckedChange = onToggle,
                    label = S.t("a11y.006")
                )
                // Beside the switch, because that is where the eye already is
                // when the answer to "what else can I do to this deck" is wanted.
                IknaIconButton(
                    glyph = IknaGlyph.DOTS,
                    onClick = onOpenDeck,
                    label = S.t("a11y.010")
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
            // Sharing used to be written out on every row, which put a
            // second button under a card that already had one and repeated the
            // same word down the whole list. It now lives on the deck's own
            // screen, where the deck is the subject and the word is needed once.
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
private fun DeckMark(deck: DeckSummary, owes: Boolean, look: DeckLook) {
    // The deck's own colour when it was given one, the palette's accent when
    // it was not. It is the same variable either way on purpose: a coloured
    // deck is not a new kind of square, it is this square in another colour,
    // and the three states below keep working without knowing the difference.
    val accent = deckTintColor(look.tint, MaterialTheme.colorScheme.primary)
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
        // The typed label if there is one, otherwise the two letters the app
        // works out by itself. One Text either way: a label is not a different
        // kind of mark, it is the same mark with better letters in it, so it
        // inherits the tint, the fade and the inversion without asking.
        Text(
            text = look.label.ifEmpty { monogramOf(deck.lang, deck.title) },
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

/**
 * "~4 мин" beside what a deck owes today, when there is a measurement to say
 * it with.
 *
 * A count of cards does not answer the question being asked while looking at
 * this list, which is whether this fits into the time there is. The figure is
 * the same one shown above the first card of a session, from the same
 * measurement: two phrasings of one number read as two numbers.
 *
 * Empty when nothing has been measured yet. Nothing is better than a guess here
 * -- an estimate that turns out to be a lie is not used again.
 */
private fun minutesTail(count: Int, perCardMs: Long?): String {
    if (perCardMs == null || count <= 0) return ""
    val totalMs = count * perCardMs
    if (totalMs < 45_000L) return S.t("deck.018")
    val minutes = ((totalMs + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    return " · ~" + minutes + S.t("deck.019")
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

