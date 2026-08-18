package dev.ikna

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.SchedulerMigrationState
import dev.ikna.ui.migration.SchedulerMigrationScreen
import dev.ikna.ui.nav.IknaNavHost
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Set by the reminder notification and by the home screen widget: open
         * the cards, not the deck list.
         *
         * Both of those are already an answer to "shall I study now" - the
         * person tapped them on purpose. Landing on a list and making them
         * choose a deck reopens a question they just closed, and that is exactly
         * the gap where the intention is lost.
         */
        const val EXTRA_START_SESSION = "dev.ikna.START_SESSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launcher window wears Theme.Ikna.Launch, which paints the app mark
        // instead of flashing a black rectangle on the way in. Hand the window
        // back to the real theme here, before super and before any drawing, or
        // the splash drawable stays behind the app for the whole session.
        setTheme(R.style.Theme_Ikna)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as IknaApp).container

        // Read once, from the intent that started this instance. The notification
        // and the widget both clear the top of the task, so a fresh onCreate is
        // what arrives here even when the app was already running.
        val startSession = intent?.getBooleanExtra(EXTRA_START_SESSION, false) == true

        setContent {
            val settings by container.settings.flow.collectAsState(initial = IknaSettings())
            val schedulerMigration by container.schedulerMigration.collectAsState()
            // The phone's own light/dark switch is read here and nowhere else,
            // and it decides one thing only: the lighting. Which palette the app
            // wears is the user's choice and does not change at sunset.
            val palette = paletteFor(settings, systemDark = isSystemInDarkTheme())

            // The interface language, resolved here and nowhere else. "system"
            // means whatever the phone is set to, so a person who never opens
            // settings still gets their own language. Changing it redraws the
            // screens in place: no activity restart, so nothing is lost from the
            // card that is open at that moment.
            LaunchedEffect(settings.language) {
                S.apply(settings.language)
            }

            // The status and navigation icons have to follow the chosen palette,
            // not the system's idea of dark mode. With a custom background this is
            // not a detail: a light background under white status icons means the
            // clock is invisible at the top of every screen, and the app cannot
            // know which way round it is without measuring the colour.
            LaunchedEffect(palette.light) {
                val style = if (palette.light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }

            // The font the user installed, or nothing at all if they have not.
            // Resolved once here, so every screen is set in it without any of
            // them knowing that a custom font is a possibility.
            val contentFont = rememberContentFont(settings.fontName)

            IknaTheme(palette = palette, contentFont = contentFont) {
                when (schedulerMigration) {
                    is SchedulerMigrationState.Ready -> IknaNavHost(
                        container = container,
                        settings = settings,
                        startSession = startSession
                    )

                    SchedulerMigrationState.Running -> SchedulerMigrationScreen(
                        failed = false,
                        onRetry = container::startSchedulerMigration
                    )

                    is SchedulerMigrationState.Failed -> SchedulerMigrationScreen(
                        failed = true,
                        onRetry = container::startSchedulerMigration
                    )
                }
            }
        }
    }
}
