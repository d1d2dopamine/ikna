package dev.ikna.desktop
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentSize
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
import dev.ikna.data.prefs.HotkeyBindings
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.decks.IknaDeckRow
import dev.ikna.data.repo.TodayStudySummary
import dev.ikna.ui.decks.IknaTodayBlock
import dev.ikna.ui.decks.IknaHomeChrome
import dev.ikna.ui.decks.HomeDestination
import dev.ikna.ui.nav.sharedAxisEnter
import dev.ikna.ui.nav.sharedAxisExit
import dev.ikna.ui.onboarding.FirstStudyDestination
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaDeveloperBadge
import dev.ikna.ui.theme.IknaElementInspector
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaHomeBackdrop
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaPanel
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.IknaTextButton
import kotlinx.coroutines.CancellationException
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont
import dev.ikna.ui.theme.iknaInspect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which screen the content area is showing. */
enum class Pane { SESSION, BROWSE, DECK, STATS, SETTINGS, ADD, CATALOG, SEARCH, ANKI, BACKUP, SUPPORT }

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
    var browseDeck by mutableStateOf<String?>(null)
    var showShortcuts by mutableStateOf(false)

    /**
     * Whether the deck screen is showing in a window too narrow to keep it
     * beside the content. Wide windows use a separate study-panel preference.
     */
    var listOpen by mutableStateOf(false)
    var studyDecksCollapsed by mutableStateOf(false)
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

    /** Open the optional reading queue for one deck. */
    fun browse(deckId: String) {
        browseDeck = deckId
        pane = Pane.BROWSE
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

    /** Apply the first-launch choice without changing ordinary return navigation. */
    fun completeFirstRun(destination: FirstStudyDestination) {
        resetForFirstRun()
        show(when (destination) {
            FirstStudyDestination.SESSION -> Pane.SESSION
            FirstStudyDestination.CATALOGUE -> Pane.CATALOG
        })
    }

    /** Everything held only by the running window must match a first launch. */
    fun resetForFirstRun() {
        pane = Pane.SESSION
        openDeck = null
        sessionDeck = null
        browseDeck = null
        showShortcuts = false
        listOpen = false
        studyDecksCollapsed = false
        reload += 1
    }
}

@Composable
fun IknaDesktopApp(
    container: DesktopContainer,
    ui: DesktopUi,
    onRestartRequested: () -> Unit,
    titleBar: @Composable (IknaPalette, Boolean) -> Unit = { _, _ -> }
) {
    // Null means the DataStore has not answered yet. Using a default settings
    // object as the loading value would flash onboarding on every ordinary
    // launch, because onboardingDone defaults to false.
    val storedSettings by container.settings.flow.collectAsState(
        initial = null as IknaSettings?
    )
    val settings = storedSettings ?: IknaSettings()

    // A desktop window has no reliable light/dark signal to read, so the
    // lighting follows the chosen palette and defaults to dark, which is what
    // the app has always looked like.
    val palette = paletteFor(settings, systemDark = true)

    LaunchedEffect(settings.language, settings.pseudoLocale) {
        S.apply(settings.language, settings.pseudoLocale)
    }

    // Saving the flag can recompose before the preparation coroutine resumes on
    // Main. Keep its pane alive until the successful callback applies the route.
    var firstRunVisible by remember { mutableStateOf(false) }
    LaunchedEffect(storedSettings?.onboardingDone) {
        if (storedSettings?.onboardingDone == false) {
            firstRunVisible = true
            ui.resetForFirstRun()
        }
    }

    val contentFont = rememberContentFont(settings.fontMode, settings.fontName)

    var ready by remember { mutableStateOf(false) }
    var startupFailed by remember { mutableStateOf(false) }
    var installAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(installAttempt) {
        ready = false
        startupFailed = false
        try {
            withContext(Dispatchers.IO) { container.install() }
            ready = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logLine("install failed: " + error)
            startupFailed = true
        }
    }

    IknaTheme(
        palette = palette,
        contentFont = contentFont,
        motionEnabled = settings.animations,
        appearanceVariant = settings.appearanceVariant
    ) {
        IknaElementInspector(enabled = settings.elementInspector) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val deckHeadingVisible = if (maxWidth >= 900.dp) {
                    ui.pane != Pane.SESSION || !ui.studyDecksCollapsed
                } else ui.listOpen
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .iknaInspect("IknaDesktopApp")
                ) {
                    // Keep the draggable strip and window controls during welcome,
                    // but do not repeat ikna in its top-left corner.
                    titleBar(palette, ready && settings.onboardingDone && !firstRunVisible && !deckHeadingVisible)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (startupFailed && storedSettings != null) {
                        Column(
                            Modifier.fillMaxSize().padding(Space.lg),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(S.t("load.004"), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(Space.md))
                            IknaTextButton(S.t("load.002"), onClick = { installAttempt++ })
                        }
                    } else if (!ready || storedSettings == null) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IknaWordmark(height = 22.dp, ink = palette.ink, dot = palette.accent)
                            Spacer(Modifier.height(Space.lg))
                            Box(Modifier.width(220.dp)) { IknaLatticePlaceholder() }
                        }
                    } else if (!settings.onboardingDone || firstRunVisible) {
                        DesktopOnboardingPane(container) { destination ->
                            ui.completeFirstRun(destination)
                            firstRunVisible = false
                        }
                    } else {
                        DesktopShell(container, settings, palette, ui, onRestartRequested)
                    }

                    if (ui.showShortcuts) {
                        ShortcutsOverlay(palette, settings) { ui.showShortcuts = false }
                    }
                    if (container.isDeveloperMode) {
                        IknaDeveloperBadge(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                        )
                    }
                }
            }
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
    ui: DesktopUi,
    onRestartRequested: () -> Unit
) {
    val deckListState = rememberLazyListState()
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var remaining by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var todaySummary by remember { mutableStateOf(TodayStudySummary()) }

    LaunchedEffect(ui.reload) {
        val nextDecks = runCatching { container.deckRepository.decks() }.getOrDefault(emptyList())
        decks = nextDecks
        remaining = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
        todaySummary = runCatching { container.learningRepository.todayStudySummary() }
            .getOrDefault(TodayStudySummary())

    }

    BoxWithConstraints(Modifier.fillMaxSize().iknaInspect("DesktopShell")) {
        val wide = maxWidth >= 900.dp
        // The deck column keeps a proportion, not a pixel count.
        //
        // A fixed 340dp is a third of a small window and a seventh of a large
        // one, and at the small end it was taking the width the pane beside it
        // needed, which is where the numbers started wrapping and moving. Three
        // steps instead: 300dp is the narrowest the bottom bar's four controls
        // sit in without touching, and it is only used when the
        // window is near its floor.
        val listWidth = when {
            maxWidth >= 1400.dp -> 380.dp
            maxWidth >= 1180.dp -> 340.dp
            else -> 300.dp
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                // The panes separate by surface tone, not by a line: the deck
                // column wears the panel tone, the content keeps the window
                // background (docs/ai-audits.md 7.4, variant 1).
                val expanded = ui.pane != Pane.SESSION || !ui.studyDecksCollapsed
                val sidebarWidth by animateDpAsState(
                    targetValue = if (expanded) listWidth else 0.dp,
                    animationSpec = if (settings.animations) tween(240) else snap(),
                    label = "deck-sidebar"
                )
                Box(Modifier.width(sidebarWidth).fillMaxHeight()
                    .clipToBounds().background(MaterialTheme.colorScheme.surface)) {
                    if (sidebarWidth > 0.dp) {
                        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).width(listWidth).fillMaxHeight()) {
                            DecksColumn(settings, palette, ui, decks, remaining, todaySummary, deckListState, showWordmark = expanded)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PaneContent(container, settings, palette, ui, decks, wide = true, onRestartRequested = onRestartRequested)
                }
            }
        } else if (ui.listOpen) {
            DecksColumn(settings, palette, ui, decks, remaining, todaySummary, deckListState)
        } else {
            PaneContent(container, settings, palette, ui, decks, wide = false, onRestartRequested = onRestartRequested)
        }
    }
}

/** The shared home layout persists beside the active pane in a wide window. */
@Composable
private fun DecksColumn(
    settings: IknaSettings,
    palette: IknaPalette,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    remaining: Map<String, Int>,
    todaySummary: TodayStudySummary,
    listState: LazyListState,
    showWordmark: Boolean = true
) {

    Box(Modifier.fillMaxSize().iknaInspect("DecksColumn")) {
        IknaHomeBackdrop()

        val activeDestination = when (ui.pane) {
            Pane.STATS -> HomeDestination.STATS
            Pane.SETTINGS, Pane.BACKUP -> HomeDestination.SETTINGS
            Pane.SEARCH -> HomeDestination.SEARCH
            Pane.SUPPORT -> HomeDestination.SUPPORT
            Pane.ADD, Pane.CATALOG, Pane.ANKI -> HomeDestination.ADD
            else -> null
        }
        IknaHomeChrome(
            dockAtTop = settings.homeDockAtTop,
            leftHanded = settings.leftHanded,
            selected = activeDestination,
            showWordmark = showWordmark,
            onNavigate = { destination ->
                ui.show(when (destination) {
                    HomeDestination.STATS -> Pane.STATS
                    HomeDestination.SETTINGS -> Pane.SETTINGS
                    HomeDestination.SEARCH -> Pane.SEARCH
                    HomeDestination.ADD -> Pane.ADD
                    HomeDestination.SUPPORT -> Pane.SUPPORT
                })
            }
        ) {
            Column(Modifier.padding(horizontal = Edge)) {
                Spacer(Modifier.height(Space.md))
                IknaTodayBlock(todaySummary, settings.answerMs.takeIf { it > 0 }?.toLong()) {
                    ui.study(null)
                }
                Spacer(Modifier.height(Space.xl))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Edge),
                verticalArrangement = Arrangement.spacedBy(Space.lg)
            ) {
                items(decks, key = { it.id }) { deck ->
                    IknaDeckRow(
                        deck = deck,
                        look = settings.lookFor(deck.id),
                        dueToday = remaining[deck.id] ?: 0,
                        onOpen = { ui.openDeckScreen(deck.id) }
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
    wide: Boolean,
    onRestartRequested: () -> Unit
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
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .clipToBounds()
            .iknaInspect("PaneContent[${ui.pane.name}]"),
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
                onBack = back,
                // In a wide window the session is the app's home: back has
                // nowhere to take the learner, so the button stays hidden.
                showBack = !wide,
                decksCollapsed = ui.studyDecksCollapsed,
                onToggleDecks = if (wide) ({ ui.studyDecksCollapsed = !ui.studyDecksCollapsed }) else null
            )

            Pane.BROWSE -> {
                val id = ui.browseDeck
                if (id != null) {
                    BrowsePane(
                        container = container,
                        settings = settings,
                        palette = palette,
                        deckId = id,
                        onChanged = { ui.refresh() },
                        onBack = back
                    )
                } else {
                    Centered(S.t("pc.004"), palette)
                }
            }

            Pane.DECK -> {
                val id = ui.openDeck
                if (id != null) {
                    DeckPane(
                        container = container,
                        settings = settings,
                        palette = palette,
                        deckId = id,
                        onStudy = { ui.study(id) },
                        onBrowse = { ui.browse(id) },
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

            Pane.SETTINGS -> SettingsPane(
                container = container,
                settings = settings,
                palette = palette,
                onOpenBackup = { ui.show(Pane.BACKUP) },
                onOpenDeveloper = { target, deck ->
                    if (container.isDeveloperMode) when (target) {
                        dev.ikna.data.dev.DeveloperDestination.SESSION -> ui.study(deck)
                        dev.ikna.data.dev.DeveloperDestination.BROWSE -> deck?.let(ui::browse)
                        dev.ikna.data.dev.DeveloperDestination.STATS -> ui.show(Pane.STATS)
                        dev.ikna.data.dev.DeveloperDestination.CATALOG -> ui.show(Pane.CATALOG)
                        dev.ikna.data.dev.DeveloperDestination.SEARCH -> ui.show(Pane.SEARCH)
                    }
                },
                onWiped = { ui.resetForFirstRun() },
                onRestartRequested = onRestartRequested,
                onBack = back
            )

            Pane.ADD -> AddDeckPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onOpenCatalog = { ui.show(Pane.CATALOG) },
                onOpenAnki = { ui.show(Pane.ANKI) },
                onBack = back
            )

            // Reached from the add screen or from a dropped package, so back
            // means the add screen, not the deck list.
            Pane.ANKI -> AnkiPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onBack = { ui.show(Pane.ADD) }
            )

            Pane.CATALOG -> CatalogPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onBack = { ui.show(Pane.ADD) }
            )

            // Back goes to settings rather than the deck list, because
            // that is where this screen is reached from. A dropped file
            // lands here too, and settings is still the nearest place.
            Pane.SUPPORT -> dev.ikna.ui.decks.IknaSupportScreen(onBack = back)

            Pane.BACKUP -> BackupPane(
                container = container,
                palette = palette,
                onChanged = { ui.refresh() },
                onBack = { ui.show(Pane.SETTINGS) }
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
private fun ShortcutsOverlay(
    palette: IknaPalette,
    settings: IknaSettings,
    onClose: () -> Unit
) {
    val bindings = remember(settings.hotkeys) { HotkeyBindings.decode(settings.hotkeys) }
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
                bindings.forEach { (action, chord) ->
                    ShortcutLine(hotkeyDisplay(chord), hotkeyActionLabel(action), palette)
                }
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
