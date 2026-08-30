package dev.ikna.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.nav.sharedAxisEnter
import dev.ikna.ui.nav.sharedAxisExit
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaPanel
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which screen the content area is showing. */
enum class Pane { SESSION, DECK, STATS, SETTINGS }

/**
 * The state the window agrees on with its menu bar.
 *
 * A plain object rather than composable state hoisted somewhere: the menu bar
 * and the window's key handler are created outside the shell's composition and
 * both have to be able to change the screen. The fields are snapshot state, so
 * changing one from a menu item redraws the shell exactly as a click on the rail
 * would.
 */
class DesktopUi {
    var pane by mutableStateOf(Pane.SESSION)
    var openDeck by mutableStateOf<String?>(null)
    var sessionDeck by mutableStateOf<String?>(null)
    var showShortcuts by mutableStateOf(false)

    /**
     * Whether the deck column is shown in a window too narrow to keep it.
     *
     * In a wide window the list is simply always there, and this is not read.
     */
    var listOpen by mutableStateOf(false)
    var reload by mutableStateOf(0)

    fun show(target: Pane) {
        pane = target
    }

    fun refresh() {
        reload += 1
    }
}

@Composable
fun IknaDesktopApp(container: DesktopContainer, ui: DesktopUi) {
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    // A desktop window has no reliable light/dark signal to read, so the
    // lighting follows the chosen palette and defaults to dark, which is what
    // the app has always looked like.
    val palette = paletteFor(settings, systemDark = true)

    LaunchedEffect(settings.language) { S.apply(settings.language) }

    val contentFont = rememberContentFont(settings.fontName)

    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { container.install() }
                .onFailure { error -> logLine("install failed: " + error) }
        }
        ready = true
    }

    IknaTheme(
        palette = palette,
        contentFont = contentFont,
        motionEnabled = settings.animations
    ) {
        Box(Modifier.fillMaxSize().background(palette.background)) {
            if (!ready) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IknaWordmark(height = 22.dp, ink = palette.ink, dot = palette.accent)
                    Spacer(Modifier.height(Space.lg))
                    Box(Modifier.width(220.dp)) { IknaLatticePlaceholder() }
                }
            } else {
                DesktopShell(container, settings, palette, ui)
            }

            if (ui.showShortcuts) {
                ShortcutsOverlay(palette) { ui.showShortcuts = false }
            }
        }
    }
}

/**
 * The window.
 *
 * A phone shows one screen at a time because it has to, and everything it owns
 * hangs off a bar at the bottom under a thumb. A window is wide, is driven by a
 * pointer and a keyboard, and can hold three things side by side: the rail of
 * destinations, the decks, and whatever is being done. That is the whole
 * difference between this file and the phone -- the marks, the rules, the
 * squares and the type are the same objects out of :shared, arranged for a
 * screen that is wider than it is tall.
 *
 * Three widths:
 *   under 900dp   rail and content; the deck list becomes a screen of its own
 *   900 to 1400   rail, a 300dp deck list, content
 *   over 1400     the same with a wider list and more air around the content
 */
@Composable
private fun DesktopShell(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    ui: DesktopUi
) {
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var remaining by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var answered by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf(0) }

    LaunchedEffect(ui.reload) {
        decks = runCatching { container.deckRepository.decks() }.getOrDefault(emptyList())
        remaining = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
        answered = runCatching { container.learningRepository.answeredToday() }.getOrDefault(0)
        target = runCatching { container.learningRepository.currentDailyTarget() }.getOrDefault(0)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val roomy = maxWidth >= 1400.dp
        val wide = maxWidth >= 900.dp
        val listWidth = if (roomy) 340.dp else 300.dp
        val edge = if (roomy) 40.dp else Space.lg

        Row(Modifier.fillMaxSize()) {
            Rail(palette, settings, ui, narrow = !wide)
            VerticalRule(palette)

            if (wide || ui.listOpen) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    DeckColumn(
                        settings = settings,
                        palette = palette,
                        ui = ui,
                        decks = decks,
                        remaining = remaining,
                        answered = answered,
                        target = target
                    )
                }
                VerticalRule(palette)
            }

            Column(Modifier.weight(1f).fillMaxHeight()) {
                ContentHeader(palette, ui, decks, answered, target, wide)
                IknaRule(color = palette.line)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PaneContent(
                        container = container,
                        settings = settings,
                        palette = palette,
                        ui = ui,
                        decks = decks,
                        remaining = remaining,
                        answered = answered,
                        target = target,
                        wide = wide,
                        edge = edge
                    )
                }
            }
        }
    }
}

/** The destinations, down the left edge. The phone's bottom bar, stood up. */
@Composable
private fun Rail(
    palette: IknaPalette,
    settings: IknaSettings,
    ui: DesktopUi,
    narrow: Boolean
) {
    Column(
        Modifier.width(64.dp).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Space.md))
        if (settings.showWordmark) {
            IknaWordmark(height = 14.dp, ink = palette.ink, dot = palette.accent)
        }
        Spacer(Modifier.height(Space.md))

        RailButton(IknaGlyph.SPARK, S.t("pc.003"), ui.pane == Pane.SESSION, palette) {
            ui.sessionDeck = null
            ui.show(Pane.SESSION)
        }
        // No button for the decks in a wide window: the list is a permanent
        // column two centimetres to the right of this rail, and a button that
        // opens what is already open is a button that does nothing. It comes
        // back only when the window is too narrow to keep the column, and then
        // it shows and hides that column instead of switching screens.
        if (narrow) {
            RailButton(IknaGlyph.STACK, S.t("pc.015"), ui.listOpen, palette) {
                ui.listOpen = !ui.listOpen
            }
        }
        RailButton(IknaGlyph.BARS, S.t("stats.001"), ui.pane == Pane.STATS, palette) {
            ui.show(Pane.STATS)
        }

        Spacer(Modifier.weight(1f))

        RailButton(IknaGlyph.GEAR, S.t("set.012"), ui.pane == Pane.SETTINGS, palette) {
            ui.show(Pane.SETTINGS)
        }
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun VerticalRule(palette: IknaPalette) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(palette.line))
}

/**
 * The decks, and what the day asks for.
 *
 * The one pairing that earns permanent room in a window: the list is how a deck
 * is chosen and how what is left is seen, and on the phone both directions cost
 * a screen change.
 */
@Composable
private fun DeckColumn(
    settings: IknaSettings,
    palette: IknaPalette,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    remaining: Map<String, Int>,
    answered: Int,
    target: Int
) {
    Column(Modifier.fillMaxSize().padding(vertical = Space.lg)) {
        Column(Modifier.padding(horizontal = Space.lg)) {
            Text(
                text = S.t("pc.016"),
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                text = answered.toString() + " / " + target,
                style = MaterialTheme.typography.headlineSmall,
                color = palette.ink
            )
            Spacer(Modifier.height(Space.sm))
            IknaProgress(
                fraction = if (target <= 0) 0f else answered.toFloat() / target,
                color = palette.accent,
                track = true
            )
        }

        Spacer(Modifier.height(Space.lg))
        IknaRule(color = palette.line)
        Spacer(Modifier.height(Space.md))

        Text(
            text = S.t("pc.015"),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = Space.lg)
        )
        Spacer(Modifier.height(Space.sm))

        if (decks.isEmpty()) {
            Text(
                text = S.t("deck.005"),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = Space.lg)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                items(decks, key = { it.id }) { deck ->
                    DeckListRow(
                        deck = deck,
                        look = settings.lookFor(deck.id),
                        due = remaining[deck.id] ?: 0,
                        selected = ui.openDeck == deck.id && ui.pane == Pane.DECK,
                        palette = palette,
                        onStudy = {
                            ui.sessionDeck = deck.id
                            ui.show(Pane.SESSION)
                        },
                        onOpen = {
                            ui.openDeck = deck.id
                            ui.show(Pane.DECK)
                        }
                    )
                }
            }
        }
    }
}

/** The title of what is on screen, and the day's figure when there is no list. */
@Composable
private fun ContentHeader(
    palette: IknaPalette,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    answered: Int,
    target: Int,
    wide: Boolean
) {
    val title = when (ui.pane) {
        Pane.SESSION -> decks.firstOrNull { it.id == ui.sessionDeck }?.title ?: S.t("pc.003")
        Pane.DECK -> decks.firstOrNull { it.id == ui.openDeck }?.title ?: S.t("pc.015")
        Pane.STATS -> S.t("stats.001")
        Pane.SETTINGS -> S.t("set.012")
    }
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = Space.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink,
            modifier = Modifier.weight(1f)
        )
        if (!wide) {
            Text(
                text = S.t("pc.016") + "  " + answered + " / " + target,
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted
            )
        }
    }
}

@Composable
private fun PaneContent(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    remaining: Map<String, Int>,
    answered: Int,
    target: Int,
    wide: Boolean,
    edge: androidx.compose.ui.unit.Dp
) {
    val travel = with(LocalDensity.current) { Motion.sharedAxisTravel.roundToPx() }
    val motion = settings.animations

    AnimatedContent(
        targetState = ui.pane,
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            sharedAxisEnter(motion, forward, travel) togetherWith
                sharedAxisExit(motion, forward, travel)
        },
        label = "pane"
    ) { pane ->
        when (pane) {
            Pane.SESSION -> SessionPane(
                container = container,
                settings = settings,
                palette = palette,
                deckId = ui.sessionDeck,
                onChanged = { ui.refresh() }
            )

            Pane.DECK -> {
                val id = ui.openDeck
                if (id != null) {
                    DeckPane(
                        container = container,
                        settings = settings,
                        palette = palette,
                        deckId = id,
                        onChanged = { ui.refresh() },
                        onDeleted = {
                            ui.openDeck = null
                            ui.show(Pane.SESSION)
                            ui.refresh()
                        }
                    )
                } else if (wide) {
                    Centered(S.t("pc.004"), palette)
                } else {
                    // No room for the permanent list, so the list is the screen.
                    DeckColumn(
                        settings = settings,
                        palette = palette,
                        ui = ui,
                        decks = decks,
                        remaining = remaining,
                        answered = answered,
                        target = target
                    )
                }
            }

            Pane.STATS -> Box(Modifier.fillMaxSize().padding(horizontal = edge)) {
                StatsPane(container, palette)
            }

            Pane.SETTINGS -> Box(Modifier.fillMaxSize().padding(horizontal = edge)) {
                Box(Modifier.widthIn(max = 720.dp)) {
                    SettingsPane(container, settings, palette)
                }
            }
        }
    }
}

/** What the keyboard can do, on F1. */
@Composable
private fun ShortcutsOverlay(palette: IknaPalette, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(palette.background.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.widthIn(max = 520.dp).padding(Space.lg)) {
            IknaPanel {
                Text(
                    text = S.t("pc.010"),
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink
                )
                Spacer(Modifier.height(Space.sm))
                ShortcutLine("Ctrl + 1", S.t("pc.003"), palette)
                ShortcutLine("Ctrl + 2", S.t("pc.015"), palette)
                ShortcutLine("Ctrl + 3", S.t("stats.001"), palette)
                ShortcutLine("Ctrl + 4", S.t("set.012"), palette)
                ShortcutLine("F11", S.t("pc.009"), palette)
                ShortcutLine("Ctrl + Q", S.t("pc.008"), palette)
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = S.t("pc.002"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
                Text(
                    text = S.t("pc.014"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
                Spacer(Modifier.height(Space.sm))
                IknaButton(S.t("sess.014"), palette, filled = true) { onClose() }
            }
        }
    }
}

@Composable
private fun ShortcutLine(keys: String, what: String, palette: IknaPalette) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = keys,
            style = MaterialTheme.typography.labelSmall,
            color = palette.accent,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = what,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.ink
        )
    }
}
