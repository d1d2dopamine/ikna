package dev.ikna.desktop
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.HotkeyBindings
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.lookFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.update.DesktopUpdateRelease
import dev.ikna.data.update.UpdateCheck
import dev.ikna.ui.decks.IknaDeckRow
import dev.ikna.data.repo.TodayStudySummary
import dev.ikna.ui.decks.IknaTodayBlock
import dev.ikna.ui.decks.IknaHomeChrome
import dev.ikna.ui.decks.IknaHomeDock
import dev.ikna.ui.decks.HomeDestination
import dev.ikna.ui.nav.sharedAxisEnter
import dev.ikna.ui.nav.sharedAxisExit
import dev.ikna.ui.onboarding.FirstStudyDestination
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaBrandPatternBackdrop
import dev.ikna.ui.theme.IknaDeveloperBadge
import dev.ikna.ui.theme.IknaElementInspector
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaHomeBackdrop
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaPanel
import dev.ikna.ui.theme.LocalIknaUnifiedFooter
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import kotlinx.coroutines.CancellationException
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont
import dev.ikna.ui.theme.iknaInspect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which screen the content area is showing. */
enum class Pane { SESSION, BROWSE, DECK, STATS, SETTINGS, ADD, CATALOG, SEARCH, ANKI, BACKUP, SUPPORT }

private fun Pane.homeDestination(): HomeDestination? = when (this) {
    Pane.STATS -> HomeDestination.STATS
    Pane.SETTINGS, Pane.BACKUP -> HomeDestination.SETTINGS
    Pane.SEARCH -> HomeDestination.SEARCH
    Pane.SUPPORT -> HomeDestination.SUPPORT
    Pane.ADD, Pane.CATALOG, Pane.ANKI -> HomeDestination.ADD
    else -> null
}

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

    internal fun navigateHome(destination: HomeDestination) {
        show(when (destination) {
            HomeDestination.STATS -> Pane.STATS
            HomeDestination.SETTINGS -> Pane.SETTINGS
            HomeDestination.SEARCH -> Pane.SEARCH
            HomeDestination.ADD -> Pane.ADD
            HomeDestination.SUPPORT -> Pane.SUPPORT
        })
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
        deckBackgroundAnimated = settings.deckBackgroundAnimation,
        uiSoundsEnabled = settings.uiSounds,
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
                            Box(Modifier.width(220.dp)) { IknaLatticePlaceholder(loading = true) }
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
    var decksLoaded by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var todaySummary by remember { mutableStateOf(TodayStudySummary()) }
    val scope = rememberCoroutineScope()
    var checkedForUpdate by remember { mutableStateOf(false) }
    var desktopUpdate by remember { mutableStateOf<DesktopUpdateRelease?>(null) }

    LaunchedEffect(settings.updateCheck, settings.updateCheckedAt) {
        if (container.isDeveloperMode || checkedForUpdate || !settings.updateCheck) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - settings.updateCheckedAt in 0 until UpdateCheck.CHECK_EVERY_MS) return@LaunchedEffect
        val release = UpdateCheck(APP_VERSION).latestDesktop(
            osName = System.getProperty("os.name").orEmpty(),
            architecture = System.getProperty("os.arch").orEmpty()
        )
        checkedForUpdate = true
        if (release != null && release.version != settings.updateSkipped) desktopUpdate = release
        container.settings.markUpdateChecked(now)
    }
    val offeredUpdate = desktopUpdate.takeIf { settings.updateCheck }
    val skipUpdate: () -> Unit = {
        desktopUpdate?.let { release ->
            desktopUpdate = null
            scope.launch { container.settings.skipUpdate(release.version, System.currentTimeMillis()) }
        }
    }

    LaunchedEffect(ui.reload) {
        val decksResult = runCatching { container.deckRepository.decks() }
        if (decksResult.isSuccess) {
            decks = decksResult.getOrThrow()
            decksLoaded = true
        }
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
            val windowDock = settings.homeDockFullWidth && !settings.homeDockAtTop
            var sessionReportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
            LaunchedEffect(ui.pane) {
                if (ui.pane != Pane.SESSION) sessionReportAction = null
            }
            val windowDockBack: (() -> Unit)? = when (ui.pane) {
                Pane.SESSION -> null
                Pane.ANKI, Pane.CATALOG -> ({ ui.show(Pane.ADD) })
                Pane.BACKUP -> ({ ui.show(Pane.SETTINGS) })
                else -> ({ ui.show(Pane.SESSION) })
            }
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    val expanded = ui.pane != Pane.SESSION || !ui.studyDecksCollapsed
                    val sidebarWidth by animateDpAsState(
                        targetValue = if (expanded) listWidth else 0.dp,
                        animationSpec = if (settings.animations) tween(240) else snap(),
                        label = "deck-sidebar"
                    )
                    DesktopDeckSidebarViewport(sidebarWidth, listWidth) {
                        DecksColumn(settings, ui, decks, remaining, todaySummary, deckListState,
                            offeredUpdate, skipUpdate, showWordmark = expanded, dockInWindow = windowDock, wide = true)
                    }
                    if (sidebarWidth > 0.dp) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.line.copy(alpha = 0.42f))
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        CompositionLocalProvider(LocalIknaUnifiedFooter provides windowDock) {
                            PaneContent(
                                container, settings, palette, ui, decks,
                                wide = true, onRestartRequested = onRestartRequested,
                                decksLoaded = decksLoaded,
                                onSessionReportActionChanged = { sessionReportAction = it }
                            )
                        }
                    }
                }
                if (windowDock) {
                    val reportAction = if (ui.pane == Pane.SESSION) sessionReportAction else null
                    IknaHomeDock(
                        leftHanded = settings.leftHanded,
                        selected = ui.pane.homeDestination(),
                        fullWidth = true,
                        onNavigate = ui::navigateHome,
                        leading = {
                            if (ui.pane == Pane.SESSION) {
                                IknaIconButton(
                                    glyph = IknaGlyph.DECK_PANEL,
                                    onClick = { ui.studyDecksCollapsed = !ui.studyDecksCollapsed },
                                    label = S.t(if (ui.studyDecksCollapsed) "deck.panel_show" else "deck.panel_hide")
                                )
                            } else if (windowDockBack != null) {
                                IknaIconButton(
                                    glyph = IknaGlyph.BACK,
                                    onClick = windowDockBack,
                                    label = S.t("a11y.001")
                                )
                            }
                        },
                        trailing = if (reportAction != null) ({
                            IknaTextButton(
                                label = S.t("sess.044"),
                                onClick = reportAction,
                                color = palette.muted
                            )
                        }) else null
                    )
                }
            }
        } else if (ui.listOpen) {
            DecksColumn(settings, ui, decks, remaining, todaySummary, deckListState,
                offeredUpdate, skipUpdate)
        } else {
            PaneContent(container, settings, palette, ui, decks, wide = false, onRestartRequested = onRestartRequested, decksLoaded = decksLoaded)
        }
    }
}

@Composable
internal fun DesktopDeckSidebarViewport(
    visibleWidth: Dp,
    contentWidth: Dp,
    content: @Composable () -> Unit
) {
    Box(Modifier.width(visibleWidth).fillMaxHeight().clipToBounds()) {
        if (visibleWidth > 0.dp) {
            // Clip the fixed-width list while it slides. Keep height bounded so
            // the weighted deck list reserves the space above the bottom dock.
            Box(Modifier.wrapContentWidth(Alignment.Start, unbounded = true).width(contentWidth).fillMaxHeight()) {
                content()
            }
        }
    }
}

/** The shared home layout persists beside the active pane in a wide window. */
@Composable
private fun DecksColumn(
    settings: IknaSettings,
    ui: DesktopUi,
    decks: List<DeckSummary>,
    remaining: Map<String, Int>,
    todaySummary: TodayStudySummary,
    listState: LazyListState,
    update: DesktopUpdateRelease?,
    onSkipUpdate: () -> Unit,
    showWordmark: Boolean = true,
    dockInWindow: Boolean = false,
    wide: Boolean = false
) {
    val activeDeckId = when (ui.pane) {
        Pane.DECK -> ui.openDeck
        Pane.SESSION -> ui.sessionDeck
        Pane.BROWSE -> ui.browseDeck
        else -> null
    }

    Box(Modifier.fillMaxSize().iknaInspect("DecksColumn")) {
        IknaHomeBackdrop(settings.deckBackground)

        IknaHomeChrome(
            dockAtTop = settings.homeDockAtTop,
            leftHanded = settings.leftHanded,
            selected = ui.pane.homeDestination(),
            showWordmark = showWordmark,
            fullWidthDock = wide || settings.homeDockFullWidth,
            dockInWindow = dockInWindow,
            today = {
                Column(Modifier.padding(horizontal = if (wide) Space.md else Edge)) {
                    Spacer(Modifier.height(if (wide) Space.sm else Space.md))
                    IknaTodayBlock(
                        summary = todaySummary,
                        perCardMs = settings.answerMs.takeIf { it > 0 }?.toLong(),
                        compact = wide
                    ) {
                        ui.study(null)
                    }
                    Spacer(Modifier.height(if (settings.homeDockAtTop) Space.xs else if (wide) Space.md else Space.xl))
                }
            },
            onNavigate = ui::navigateHome
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) Space.sm else Edge),
                verticalArrangement = Arrangement.spacedBy(if (wide) Space.xs else Space.md)
            ) {
                if (update != null) {
                    item(key = "desktop-update") {
                        Column(Modifier.padding(horizontal = if (wide) Space.sm else 0.dp)) {
                            Text(
                                text = S.t("upd.016") + update.version,
                                style = MaterialTheme.typography.labelLarge
                            )
                            update.packages.forEach { (kind, asset) ->
                                IknaTextButton(kind.updateLabel(), onClick = { openInBrowser(asset.url) })
                            }
                            IknaTextButton(S.t("upd.006"), onClick = onSkipUpdate)
                        }
                    }
                }
                items(decks, key = { it.id }) { deck ->
                    IknaDeckRow(
                        deck = deck,
                        look = settings.lookFor(deck.id),
                        dueToday = remaining[deck.id] ?: 0,
                        onOpen = { ui.study(deck.id) },
                        onManage = { ui.openDeckScreen(deck.id) },
                        compact = wide,
                        selected = wide && activeDeckId == deck.id
                    )
                }
                if (decks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = if (wide) Space.sm else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(Space.md)
                        ) {
                            Text(
                                text = S.t("deck.005"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IknaWideButton(
                                label = S.t("a11y.004"),
                                onClick = { ui.show(Pane.ADD) },
                                filled = true,
                                height = 48.dp
                            )
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
    onRestartRequested: () -> Unit,
    decksLoaded: Boolean = false,
    onSessionReportActionChanged: ((() -> Unit)?) -> Unit = {}
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
            Pane.SESSION -> if (decksLoaded && decks.isEmpty()) {
                val unifiedFooter = LocalIknaUnifiedFooter.current
                Box(Modifier.fillMaxSize().background(palette.background).clipToBounds()) {
                    IknaBrandPatternBackdrop(intensity = 0.72f)
                    Column(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = Edge),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(Modifier.width(220.dp)) { IknaLatticePlaceholder() }
                                Spacer(Modifier.height(Space.lg))
                                Text(
                                    text = S.t("deck.empty_title"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.ink,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(Space.sm))
                                Text(
                                    text = S.t("deck.005"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.muted,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(Space.lg))
                                IknaWideButton(
                                    label = S.t("cat.033"),
                                    filled = true,
                                    modifier = Modifier.widthIn(max = 280.dp),
                                    onClick = { ui.show(Pane.CATALOG) }
                                )
                                Spacer(Modifier.height(Space.xs))
                                IknaTextButton(
                                    label = S.t("a11y.004"),
                                    onClick = { ui.show(Pane.ADD) },
                                    color = palette.muted
                                )
                            }
                        }
                        if (!unifiedFooter) {
                            IknaBottomBar {
                                if (!wide) {
                                    IknaIconButton(IknaGlyph.BACK, onClick = back, label = S.t("a11y.001"))
                                } else {
                                    IknaIconButton(
                                        glyph = IknaGlyph.DECK_PANEL,
                                        onClick = { ui.studyDecksCollapsed = !ui.studyDecksCollapsed },
                                        label = S.t(if (ui.studyDecksCollapsed) "deck.panel_show" else "deck.panel_hide")
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                SessionPane(
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
                    onToggleDecks = if (wide) ({ ui.studyDecksCollapsed = !ui.studyDecksCollapsed }) else null,
                    onReportActionChanged = onSessionReportActionChanged
                )
            }

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
            Pane.SUPPORT -> dev.ikna.ui.decks.IknaSupportScreen(installedVersion = APP_VERSION, onBack = back)

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
