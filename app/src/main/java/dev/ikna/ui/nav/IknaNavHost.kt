package dev.ikna.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.catalog.CatalogScreen
import dev.ikna.ui.debug.DebugHooks
import dev.ikna.ui.decks.AddDeckScreen
import dev.ikna.ui.decks.AnkiImportScreen
import dev.ikna.ui.decks.DeckScreen
import dev.ikna.ui.decks.DecksHomeState
import dev.ikna.ui.decks.DecksScreen
import dev.ikna.ui.onboarding.OnboardingScreen
import dev.ikna.ui.search.DeckSearchScreen
import dev.ikna.ui.session.SessionScreen
import dev.ikna.ui.settings.SettingsScreen
import dev.ikna.ui.settings.VoiceScreen
import dev.ikna.ui.stats.StatsScreen
import dev.ikna.ui.theme.Motion
import androidx.compose.material3.MaterialTheme
import dev.ikna.ui.update.UpdateGate

/**
 * Where the app can be.
 *
 * A session belongs to a deck or to the whole day, and that is part of the
 * address rather than of some ambient state: coming back from Settings has to
 * land in the same session that was left, and process death has to restore it
 * too. [ALL_DECKS] is the word used when there is no deck, because a route
 * argument cannot be absent and "" is not a legal path segment.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SESSION = "session/{deck}"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ADD_DECK = "add-deck"
    const val ANKI_IMPORT = "anki-import"
    const val CATALOG = "catalog"
    const val SEARCH = "search"
    const val VOICE = "voice"
    const val DECK = "deck/{deck}"
    const val DEBUG = "debug"

    const val ALL_DECKS = "all"

    fun session(deckId: String?): String = "session/" + (deckId ?: ALL_DECKS)

    fun deck(deckId: String): String = "deck/" + deckId
}

/**
 * Navigation, without a navigation panel.
 *
 * There used to be a drawer of five tabs, opened by dragging from the left edge
 * — a 20dp invisible strip that Android 10 and later also claims for its own
 * back gesture, so it worked about every other try and was switched off during a
 * session entirely. Five destinations behind a hidden gesture is not navigation,
 * it is a puzzle.
 *
 * What replaced it is the shape of the thing itself: decks are the home screen,
 * a session opens out of a deck and closes back into it, and the two screens
 * that are neither — progress and settings — hang off marks in the top row. The
 * "cards" tab is gone because it was a second door into the same session.
 */
@Composable
fun IknaNavHost(
    container: AppContainer,
    settings: IknaSettings,
    /** True when the app was opened from the reminder or from the widget. */
    startSession: Boolean = false
) {
    val navController = rememberNavController()

    // Home is removed from composition while a pushed route is open. These two
    // objects stay at graph scope, so Back restores the populated list and the
    // exact scroll position before its first visible frame.
    val decksState = remember { DecksHomeState() }
    val decksListState = rememberLazyListState()

    val sharedAxisTravelPx = with(LocalDensity.current) {
        Motion.sharedAxisTravel.roundToPx()
    }

    fun forward(route: String) {
        navController.navigate(route)
    }

    fun back() {
        navController.popBackStack()
    }

    // The stored flag arrives a frame or two after the first composition, and a
    // NavHost reads its start destination exactly once. Deciding from the value
    // that is merely current would show the onboarding screen for those frames on
    // every single launch, so nothing is drawn until the real flag is known.
    var onboarded by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        onboarded = runCatching { container.settings.current().onboardingDone }
            .getOrDefault(false)
    }

    // The jump into a session happens once per launch. Without this flag any
    // recomposition would push another session onto the stack, and going back
    // would walk through a pile of identical screens.
    var jumped by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Drawn edge to edge, so every screen keeps clear of the status bar
            // and the gesture area in one place instead of five.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val known = onboarded ?: return@Box

        // Someone who has not been through the introduction yet is not sent into
        // the cards, whatever tapped the icon.
        LaunchedEffect(known, startSession) {
            if (known && startSession && !jumped) {
                jumped = true
                navController.navigate(Routes.session(null))
            }
        }

        NavHost(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clipToBounds(),
            navController = navController,
            startDestination = if (known) Routes.HOME else Routes.ONBOARDING,
            // Forward navigation advances along the x axis; system Back mirrors it.
            // The host is a fixed viewport and opacity has a single hand-off, so
            // outgoing and incoming layouts never negotiate or remain readable together.
            enterTransition = {
                sharedAxisEnter(settings.animations, forward = true, travelPx = sharedAxisTravelPx)
            },
            exitTransition = {
                sharedAxisExit(settings.animations, forward = true, travelPx = sharedAxisTravelPx)
            },
            popEnterTransition = {
                sharedAxisEnter(settings.animations, forward = false, travelPx = sharedAxisTravelPx)
            },
            popExitTransition = {
                sharedAxisExit(settings.animations, forward = false, travelPx = sharedAxisTravelPx)
            }
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    container = container,
                    onDone = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                DecksScreen(
                    container = container,
                    settings = settings,
                    state = decksState,
                    listState = decksListState,
                    onOpenSession = { deckId -> forward(Routes.session(deckId)) },
                    onOpenDeck = { deckId -> forward(Routes.deck(deckId)) },
                    onOpenStats = { forward(Routes.STATS) },
                    onOpenSettings = { forward(Routes.SETTINGS) },
                    onOpenSearch = { forward(Routes.SEARCH) },
                    onAddDeck = { forward(Routes.ADD_DECK) }
                )
            }

            composable(Routes.SEARCH) {
                DeckSearchScreen(
                    container = container,
                    onBack = { back() },
                    onOpenDeck = { deckId -> forward(Routes.deck(deckId)) }
                )
            }

            // Adding a deck is a place, not a file dialog. It used to be the
            // system file browser opening straight out of the plus, which meant
            // the app never got to say what a deck file is — and there is no
            // room in a file browser to say it.
            composable(Routes.ADD_DECK) {
                AddDeckScreen(
                    container = container,
                    onOpenCatalog = { forward(Routes.CATALOG) },
                    onOpenAnki = { forward(Routes.ANKI_IMPORT) },
                    onBack = { back() }
                )
            }

            composable(Routes.ANKI_IMPORT) {
                AnkiImportScreen(
                    container = container,
                    onBack = { back() }
                )
            }

            // Decks somebody else already built, reached from the screen that
            // asks where a deck comes from. Behind that screen rather than
            // beside it: the answer "download a ready one" only makes sense
            // once the question has been asked.
            composable(Routes.CATALOG) {
                CatalogScreen(
                    container = container,
                    onBack = { back() }
                )
            }

            // One deck on its own: its language, sending it to someone, deleting
            // it. The switch that turns a deck on and off stays on the list,
            // where all of them can be compared at once.
            composable(
                route = Routes.DECK,
                arguments = listOf(navArgument("deck") { type = NavType.StringType })
            ) { entry ->
                entry.arguments?.getString("deck")?.let { deckId ->
                    DeckScreen(
                        container = container,
                        settings = settings,
                        deckId = deckId,
                        onBack = { back() }
                    )
                }
            }

            composable(
                route = Routes.SESSION,
                arguments = listOf(navArgument("deck") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("deck")
                SessionScreen(
                    container = container,
                    deckId = if (raw == null || raw == Routes.ALL_DECKS) null else raw,
                    onBack = { back() }
                )
            }

            composable(Routes.STATS) {
                StatsScreen(
                    container = container,
                    onBack = { back() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    settings = settings,
                    onOpenDebug = { forward(Routes.DEBUG) },
                    onOpenVoice = { forward(Routes.VOICE) },
                    onBack = { back() }
                )
            }

            // Which voice speaks, and where it came from. Reached from settings
            // and nowhere else: it is read once, when somebody is deciding what
            // should be reading the cards out.
            composable(Routes.VOICE) {
                VoiceScreen(
                    container = container,
                    speechEnabled = settings.speechEnabled,
                    onBack = { back() }
                )
            }

            // Registered in both builds so the graph is the same shape
            // everywhere, but in a release build DebugHooks.Screen draws nothing
            // and no button leads here. The screen itself is in src/debug only.
            composable(Routes.DEBUG) {
                DebugHooks.Screen(
                    container = container,
                    onBack = { back() }
                )
            }
        }

        // Above the graph rather than inside it, so it is asked once per
        // launch and cannot be re-asked by walking between screens. It draws
        // nothing at all until there is something to say.
        UpdateGate(container = container, settings = settings)
    }
}
