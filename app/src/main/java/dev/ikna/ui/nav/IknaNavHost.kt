package dev.ikna.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
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

object Routes {
    const val ONBOARDING = "onboarding"
    const val SESSION = "session"
    const val DECKS = "decks"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val DEBUG = "debug"
}

private data class Tab(val route: String, val label: String, val glyph: String)

private val TABS = listOf(
    Tab(Routes.SESSION, "Карточки", "◆"),
    Tab(Routes.DECKS, "Наборы", "▤"),
    Tab(Routes.STATS, "Прогресс", "▲"),
    Tab(Routes.SETTINGS, "Настройки", "⚙")
)

/**
 * Four tabs, and the technical screen hidden inside settings.
 *
 * The first version had no way out of the session screen, which made the app
 * feel like a single unfinished screen rather than a place you can look around.
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
    val showTabs = TABS.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                if (route != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Routes.SESSION) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Text(tab.glyph) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = if (start) Routes.SESSION else Routes.ONBOARDING,
            modifier = Modifier.padding(insets)
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
    }
}
