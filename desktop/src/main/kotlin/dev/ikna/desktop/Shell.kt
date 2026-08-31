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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.decks.IknaDeckRow
import dev.ikna.ui.decks.IknaTodayBlock
import dev.ikna.ui.nav.sharedAxisEnter
import dev.ikna.ui.nav.sharedAxisExit
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaMemoryField
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaPanel
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen the content area is showing. */
enum class Pane { SESSION, DECK, STATS, SETTINGS, ADD, CATALOG, SEARCH }

/**
 * The state the window agrees on with its key handler.
 *
 * A plain object rather than composable state hoisted somewhere: the window's
 * key handler is created outside the shell's composition and has to be able to
 * change the screen. The fields are snapshot state, so changing one from a
 * shortcut redraws the shell exactly as a click would.
 */
class DesktopUi {
    var pane by mutableStateOf(Pane.SESSION)
    var openDeck by mutableStateOf<String?>(null)
    var sessionDeck by mutableStateOf<String?>(null)
    var showShortcuts by mutableStateOf(false)

    /**
     * Whether the deck screen is showing in a window too narrow to keep it
     * beside the content. In a wide window the list is simply always there.
     */
    var listOpen by mutableStateOf(false)
    var reload by mutableStateOf(0)

    fun show(target: Pane) {
        pane = target
        listOpen = false
    }

    /** Start or resume the cards, for one deck or for everything due. */
    fun study(deckId: String?) {
        sessionDeck = deckId
        pane = Pane.SESSION
        listOpen = false
    }

    /** Open one deck's own screen. */
    fun openDeckScreen(deckId: String) {
        openDeck = deckId
        pane = Pane.DECK
        listOpen = false
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
 * The phone shows one screen at a time because it has to. A window is wide
 * enough to keep the deck screen -- the whole deck screen, the pixel field
 * behind it, the figure for the day, the marks, the bars and the bottom bar --
 * permanently on the left, with whatever is being done beside it. Nothing here
 * is a desktop retelling of a phone screen: every piece is the phone's own
 * object out of :shared, and the only thing this file decides is which of them
 * are on screen at the same time.
 *
 * Two widths:
 *   under 900dp   one screen at a time, the way a phone does it
 *   over 900dp    the deck screen and the content side by side
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

    LaunchedEffect(ui.reload) {
        decks = runCatching { container.deckRepository.decks() }.getOrDefault(emptyList())
        remaining = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        // The deck column keeps a proportion, not a pixel count.
        //
        // A fixed 340dp is a third of a small window and a seventh of a large
        // one, and at the small end it was taking the width the pane beside it
        // needed, which is where the numbers started wrapping and moving. Three
        // steps instead: 300dp is the narrowest the bottom bar's five buttons
        // and the wordmark sit in without touching, and it is only used when the
        // window is near its floor.
        val listWidth = when {
            maxWidth >= 1400.dp -> 380.dp
            maxWidth >= 1180.dp -> 340.dp
            else -> 300.dp
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(listWidth).fillMaxHeight()) {
                    DecksColumn(container, settings, palette, ui, decks, remaining)
                }
                VerticalRule(palette)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PaneContent(container, settings, palette, ui, decks, wide = true)
                }
            }
        } else if (ui.listOpen) {
            DecksColumn(container, settings, palette, ui, decks, remaining)
        } else {
            PaneContent(container, settings, palette, ui, decks, wide = false)
        }
    }
}

@Composable
private fun VerticalRule(palette: IknaPalette) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(palette.line))
}

/**
 * The phone's deck screen, whole.
 *
 * Same pixel field behind it, same title, same figure for the day, same rows
 * with the same squares, percentages and segmented bars, same bottom bar with
 * the wordmark, the statistics, the settings, the search and the plus. The only
 * thing the window changes is that this screen no longer has to go away for
 * something else to be shown.
 */
@Composable
private fun DecksColumn(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    remaining: Map<String, Int>
) {
    val scope = rememberCoroutineScope()
    val todayTotal = remaining.values.sum()

    Box(Modifier.fillMaxSize()) {
        IknaMemoryField(seed = 0x1A4B_7C2D, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize()) {
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
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(Modifier.padding(horizontal = Edge)) {
                Spacer(Modifier.height(Space.md))
                IknaTodayBlock(total = todayTotal) { ui.study(null) }
                Spacer(Modifier.height(Space.xl))
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Edge),
                verticalArrangement = Arrangement.spacedBy(Space.lg)
            ) {
                items(decks, key = { it.id }) { deck ->
                    IknaDeckRow(
                        deck = deck,
                        look = settings.lookFor(deck.id),
                        dueToday = remaining[deck.id] ?: 0,
                        perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
                        onOpen = { ui.study(deck.id) },
                        onOpenDeck = { ui.openDeckScreen(deck.id) },
                        onToggle = { on ->
                            scope.launch {
                                runCatching {
                                    container.deckRepository.setActive(deck.id, on)
                                    container.learningRepository.invalidatePlan()
                                }.onFailure { error -> logLine("deck toggle failed: " + error) }
                                ui.refresh()
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
                                color = palette.muted
                            )
                            Spacer(Modifier.height(Space.lg))
                            IknaLatticePlaceholder()
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.lg)) }
            }

            Spacer(Modifier.height(Space.md))

            IknaBottomBar {
                if (settings.leftHanded) {
                    IknaIconButton(
                        glyph = IknaGlyph.PLUS,
                        onClick = { ui.show(Pane.ADD) },
                        label = S.t("a11y.004")
                    )
                    IknaIconButton(
                        glyph = IknaGlyph.SEARCH,
                        onClick = { ui.show(Pane.SEARCH) },
                        label = S.t("a11y.011")
                    )
                    Spacer(Modifier.weight(1f))
                    IknaIconButton(
                        glyph = IknaGlyph.GEAR,
                        onClick = { ui.show(Pane.SETTINGS) },
                        label = S.t("a11y.002")
                    )
                    IknaIconButton(
                        glyph = IknaGlyph.BARS,
                        onClick = { ui.show(Pane.STATS) },
                        label = S.t("a11y.003")
                    )
                    if (settings.showWordmark) {
                        IknaWordmark(modifier = Modifier.padding(start = Space.md))
                    }
                } else {
                    if (settings.showWordmark) {
                        IknaWordmark(modifier = Modifier.padding(start = Space.md))
                    }
                    IknaIconButton(
                        glyph = IknaGlyph.BARS,
                        onClick = { ui.show(Pane.STATS) },
                        label = S.t("a11y.003")
                    )
                    IknaIconButton(
                        glyph = IknaGlyph.GEAR,
                        onClick = { ui.show(Pane.SETTINGS) },
                        label = S.t("a11y.002")
                    )
                    Spacer(Modifier.weight(1f))
                    IknaIconButton(
                        glyph = IknaGlyph.SEARCH,
                        onClick = { ui.show(Pane.SEARCH) },
                        label = S.t("a11y.011")
                    )
                    IknaIconButton(
                        glyph = IknaGlyph.PLUS,
                        onClick = { ui.show(Pane.ADD) },
                        label = S.t("a11y.004")
                    )
                }
            }
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
    wide: Boolean
) {
    val travel = with(LocalDensity.current) { Motion.sharedAxisTravel.roundToPx() }
    val motion = settings.animations

    // In a wide window the deck screen never leaves, so the way back out of a
    // pane is the cards. In a narrow one there is only ever one screen, and back
    // means the deck screen, exactly as it does on a phone.
    val back: () -> Unit = {
        if (wide) {
            ui.show(Pane.SESSION)
        } else {
            ui.listOpen = true
        }
    }

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
                onChanged = { ui.refresh() },
                onBack = back
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
                            ui.refresh()
                            back()
                        },
                        onBack = back
                    )
                } else {
                    Centered(S.t("pc.004"), palette)
                }
            }

            Pane.STATS -> StatsPane(container, palette, back)

            Pane.SETTINGS -> SettingsPane(container, settings, palette, back)

            Pane.ADD -> AddDeckPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onOpenCatalog = { ui.show(Pane.CATALOG) },
                onBack = back
            )

            Pane.CATALOG -> CatalogPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onBack = { ui.show(Pane.ADD) }
            )

            Pane.SEARCH -> SearchPane(
                container = container,
                palette = palette,
                decks = decks,
                onOpenDeck = { id -> ui.openDeckScreen(id) },
                onBack = back
            )
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
                ShortcutLine("Ctrl + 1", S.t("deck.007"), palette)
                ShortcutLine("Ctrl + 2", S.t("deck.004"), palette)
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
