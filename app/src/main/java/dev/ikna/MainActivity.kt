package dev.ikna

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.nav.IknaNavHost
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.paletteFor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as IknaApp).container
        setContent {
            val settings by container.settings.flow.collectAsState(initial = IknaSettings())
            val palette = paletteFor(settings)

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

            IknaTheme(palette = palette) {
                IknaNavHost(container = container, settings = settings)
            }
        }
    }
}
