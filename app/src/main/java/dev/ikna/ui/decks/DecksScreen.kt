package dev.ikna.ui.decks

import dev.ikna.ui.text.S
import dev.ikna.ui.text.quantityWord

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.domain.session.BrowseAvailability
import dev.ikna.domain.session.BrowseUnavailableReason
import dev.ikna.ui.session.browseUnavailableText
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaDeckHeaderPaint
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaMemoryField
import dev.ikna.ui.theme.IknaTransientNotice
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Space
import dev.ikna.widget.TodayWidget
import kotlinx.coroutines.launch

/**
 * Data that belongs to the Home back-stack entry, not to one composition of it.
 *
 * Navigation removes a destination from composition after its exit transition.
 * Keeping this state in [IknaNavHost] means Back can draw the last complete deck
 * list immediately, then refresh it without an empty frame in between.
 */
@Stable
class DecksHomeState {
    var decks by mutableStateOf<List<DeckSummary>>(emptyList())
        private set

    var today by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    var browseAvailability by mutableStateOf<Map<String, BrowseAvailability>>(emptyMap())
        private set

    suspend fun reload(container: AppContainer) {
        val nextDecks = container.deckRepository.decks()
        val nextToday = runCatching {
            container.learningRepository.remainingByDeck()
        }.getOrDefault(emptyMap())
        val nextBrowseAvailability = runCatching {
            container.learningRepository.browseDeckAvailability(nextDecks.map { it.id })
        }.getOrElse {
            nextDecks.associate { deck ->
                deck.id to BrowseAvailability.blocked(BrowseUnavailableReason.CHECK_FAILED)
            }
        }
        decks = nextDecks
        today = nextToday
        browseAvailability = nextBrowseAvailability
    }
}

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
    state: DecksHomeState,
    listState: LazyListState,
    onOpenSession: (String?) -> Unit,
    onOpenBrowse: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onAddDeck: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decks = state.decks
    val today = state.today

    // Re-runs whenever this screen comes back to the front, so the counts are
    // right after a session instead of a minute stale. Coming back from the
    // add-deck screen lands here too, which is how a deck imported a moment ago
    // is already in the list and already counted.
    LaunchedEffect(Unit) { state.reload(container) }

    val todayTotal = today.values.sum()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // The number on the home screen widget comes from here. A widget cannot read
    // the database from the launcher's process, so the app hands it the finished
    // text every time this screen knows a new value - and this screen is the one
    // a session returns to, so it always does.
    LaunchedEffect(todayTotal, S.lang, S.pseudo) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Only the unused field carries the lattice. Content remains on plain
        // paper, and the bottom bar paints its own background above it. The field
        // belongs to the Home destination itself, so Shared Axis fades and moves it
        // with the deck interface instead of removing it at the start of navigation.
        // Every pushed route and the NavHost paint an opaque clipped surface, so the
        // field cannot survive after Home's exit or leak into Settings.
        IknaMemoryField(seed = 0x1A4B_7C2D, modifier = Modifier.fillMaxSize())
        IknaDeckHeaderPaint(seed = 0x5D31_7A0C)
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
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Edge),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            items(decks, key = { it.id }) { deck ->
                val browse = state.browseAvailability[deck.id]
                    ?: BrowseAvailability.blocked(BrowseUnavailableReason.CHECKING)
                IknaDeckRow(
                    deck = deck,
                    look = settings.lookFor(deck.id),
                    dueToday = today[deck.id] ?: 0,
                    perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
                    onOpen = { onOpenSession(deck.id) },
                    browseAvailable = browse.available,
                    onBrowse = {
                        scope.launch {
                            val latest = runCatching {
                                container.learningRepository
                                    .browseDeckAvailability(listOf(deck.id))[deck.id]
                            }.getOrNull()
                                ?: BrowseAvailability.blocked(BrowseUnavailableReason.CHECK_FAILED)
                            if (latest.available) onOpenBrowse(deck.id)
                            else note = browseUnavailableText(latest)
                        }
                    },
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
                            state.reload(container)
                        }
                    }
                )
            }
            if (decks.isEmpty()) {
                item {
                    Column {
                        Text(
                            text = S.t("deck.005"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted
                        )
                        Spacer(Modifier.height(Space.lg))
                        IknaLatticePlaceholder()
                    }
                }
            }
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
            // The wordmark stays visible because Android has no window title bar.
            // The whole row can still be mirrored for a left hand: the
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
                IknaIconButton(
                    glyph = IknaGlyph.SEARCH,
                    onClick = onOpenSearch,
                    label = S.t("a11y.011")
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
                IknaWordmark(modifier = Modifier.padding(end = Space.md))
            } else {
                IknaWordmark(modifier = Modifier.padding(start = Space.md))
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
                    glyph = IknaGlyph.SEARCH,
                    onClick = onOpenSearch,
                    label = S.t("a11y.011")
                )
                IknaIconButton(
                    glyph = IknaGlyph.PLUS,
                    onClick = onAddDeck,
                    label = S.t("a11y.004")
                )
            }
        }
        }
        IknaTransientNotice(
            message = note,
            onDismiss = { note = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = Edge, end = Edge, bottom = BarHeight + Space.md)
        )
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

/** Android and Desktop deliberately render [IknaDeckRow] from the shared module. */
private fun cardWord(count: Int): String {
    return quantityWord(count.toLong(), "deck.014", "deck.015", "deck.016", "deck.017")
}
