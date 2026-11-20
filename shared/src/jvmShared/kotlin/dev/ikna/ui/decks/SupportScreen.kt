package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*

@Composable
fun IknaSupportScreen(onBack: () -> Unit) {
    val handler = LocalUriHandler.current
    val repo = "https://github.com/d1d2dopamine/ikna"
    val links = listOf(
        "★   GitHub · Ikna" to repo,
        "⚑   GitHub Issues" to "$repo/issues/new/choose",
        "✉   Gmail · d1d2dopamine@gmail.com" to "mailto:d1d2dopamine@gmail.com",
        "◎   GitHub · d1d2dopamine" to "https://github.com/d1d2dopamine"
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(S.t("support.title"), style = MaterialTheme.typography.headlineSmall)
            Text(S.t("support.body"), style = MaterialTheme.typography.bodyLarge)
            for ((label, url) in links) {
                IknaWideButton(label, onClick = { handler.openUri(url) }, height = 48.dp)
            }
        }
        IknaBottomBar {
            IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}
