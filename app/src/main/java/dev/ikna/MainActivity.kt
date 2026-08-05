package dev.ikna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.ui.nav.IknaNavHost
import dev.ikna.ui.theme.IknaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as IknaApp).container
        setContent {
            val settings by container.settings.flow.collectAsState(initial = IknaSettings())
            IknaTheme(mode = settings.theme) {
                IknaNavHost(container = container, settings = settings)
            }
        }
    }
}
