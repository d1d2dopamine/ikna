package dev.ikna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.ikna.ui.nav.IknaNavHost
import dev.ikna.ui.theme.IknaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as IknaApp).container
        setContent {
            IknaTheme {
                IknaNavHost(container = container)
            }
        }
    }
}
