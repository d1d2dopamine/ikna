package dev.ikna.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.ikna.AppContainer
import dev.ikna.ui.debug.DebugScreen
import dev.ikna.ui.session.SessionScreen
import dev.ikna.ui.stats.StatsScreen

object Routes {
    const val SESSION = "session"
    const val STATS = "stats"
    const val DEBUG = "debug"
}

@Composable
fun IknaNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.SESSION) {
        composable(Routes.SESSION) {
            SessionScreen(
                container = container,
                onOpenStats = { nav.navigate(Routes.STATS) }
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenDebug = { nav.navigate(Routes.DEBUG) }
            )
        }
        composable(Routes.DEBUG) {
            DebugScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
