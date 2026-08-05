package dev.ikna.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.ikna.AppContainer
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.debug.DebugScreen
import dev.ikna.ui.decks.DecksScreen
import dev.ikna.ui.onboarding.OnboardingScreen
import dev.ikna.ui.session.SessionScreen
import dev.ikna.ui.settings.SettingsScreen
import dev.ikna.ui.stats.StatsScreen
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaGlyphIcon
import dev.ikna.ui.theme.IknaMuted
import dev.ikna.ui.theme.IknaRule
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val SESSION = "session"
    const val DECKS = "decks"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

private data class Tab(val route: String, val label: String, val glyph: IknaGlyph)

private val TABS = listOf(
    Tab(Routes.SESSION, "Карточки", IknaGlyph.SPARK),
    Tab(Routes.DECKS, "Наборы", IknaGlyph.STACK),
    Tab(Routes.STATS, "Прогресс", IknaGlyph.BARS),
    Tab(Routes.SETTINGS, "Настройки", IknaGlyph.SLIDERS)
)

/** Width of the invisible strip along the left edge that the panel is pulled from. */
private val HANDLE_WIDTH = 20.dp

/**
 * No tab bar.
 *
 * The four screens live in a panel pulled out from the left edge, so the session
 * gets the entire screen instead of sharing it with navigation that is used a few
 * times a day at most. The pull zone is a narrow strip, not the whole surface:
 * the card itself is swiped horizontally, and the two gestures must not compete.
 */
@Composable
fun IknaNavHost(container: AppContainer, settings: IknaSettings) {
    // One-shot read so the start destination is correct on the very first frame;
    // reading it from the flow would briefly say "not onboarded" every launch.
    val onboarded by produceState<Boolean?>(initialValue = null) {
        value = runCatching { container.settings.current().onboardingDone }.getOrDefault(false)
    }
    val start = onboarded ?: return

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val navigable = TABS.any { it.route == route }

    ModalNavigationDrawer(
        drawerState = drawer,
        // Only while open: closing by swipe should work, opening must stay the
        // job of the edge strip so a card swipe is never mistaken for it.
        gesturesEnabled = drawer.isOpen,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        drawerContent = {
            NavPanel(
                current = route,
                onPick = { picked ->
                    scope.launch { drawer.close() }
                    if (picked != route) {
                        nav.navigate(picked) {
                            popUpTo(Routes.SESSION) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (start) Routes.SESSION else Routes.ONBOARDING,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(container = container) {
                        nav.navigate(Routes.SESSION) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                }
                composable(Routes.SESSION) {
                    SessionScreen(container = container)
                }
                composable(Routes.DECKS) {
                    DecksScreen(container = container)
                }
                composable(Routes.STATS) {
                    StatsScreen(container = container)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        settings = settings,
                        onOpenDebug = { nav.navigate(Routes.DEBUG) }
                    )
                }
                composable(Routes.DEBUG) {
                    DebugScreen(container = container, onBack = { nav.popBackStack() })
                }
            }

            if (navigable) {
                EdgeHandle(
                    tapToOpen = route != Routes.SESSION,
                    onOpen = { scope.launch { drawer.open() } }
                )
            }
        }
    }
}

/**
 * The pull tab itself: a short accent bar on the left edge.
 *
 * Visible enough to be found once, quiet enough to be forgotten afterwards. On
 * the session screen it only responds to a pull, because a tap belongs to the
 * card.
 */
@Composable
private fun EdgeHandle(tapToOpen: Boolean, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(HANDLE_WIDTH)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 6f) onOpen()
                }
            }
            .then(if (tapToOpen) Modifier.clickable(onClick = onOpen) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        )
    }
}

@Composable
private fun NavPanel(current: String?, onPick: (String) -> Unit) {
    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
        drawerTonalElevation = 0.dp,
        modifier = Modifier.width(272.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                modifier = Modifier.padding(start = 24.dp, top = 28.dp, bottom = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IknaGlyphIcon(
                    glyph = IknaGlyph.SPARK,
                    color = MaterialTheme.colorScheme.primary,
                    size = 18.dp
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "IKNA",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IknaRule()

            TABS.forEach { tab ->
                val selected = tab.route == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onPick(tab.route) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                    Spacer(Modifier.width(20.dp))
                    IknaGlyphIcon(
                        glyph = tab.glyph,
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else IknaMuted,
                        size = 20.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) MaterialTheme.colorScheme.onBackground
                        else IknaMuted
                    )
                }
                IknaRule()
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "потяни от левого края, чтобы открыть это снова",
                style = MaterialTheme.typography.labelSmall,
                color = IknaMuted,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
            )
        }
    }
}
