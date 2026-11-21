package dev.ikna.ui.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.data.update.UpdateCheck
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*

private const val REPOSITORY = "https://github.com/d1d2dopamine/ikna"

private enum class SupportDestination(val label: String, val url: String, val icon: SupportBrandIcon) {
    REPO("support.repo", REPOSITORY, SupportBrandIcon.REPOSITORY),
    ISSUE("support.issue", "$REPOSITORY/issues/new/choose", SupportBrandIcon.ISSUE),
    MAIL("support.mail", "mailto:d1d2dopamine@gmail.com", SupportBrandIcon.GMAIL),
    AUTHOR("support.author", "https://github.com/d1d2dopamine", SupportBrandIcon.AUTHOR)
}

@Composable
fun IknaSupportScreen(installedVersion: String, onBack: () -> Unit) {
    val handler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = Edge, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IknaWordmark(height = 28.dp)
            Text(S.t("support.title"), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
            Text(S.t("support.description"), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Text(S.t("upd.011") + installedVersion, style = MaterialTheme.typography.labelMedium)
            IknaTextButton(S.t("support.changelog"), onClick = {
                failed = runCatching { handler.openUri(UpdateCheck.RELEASES_PAGE) }.isFailure
            })
            Spacer(Modifier.height(Space.sm))
            Text(S.t("support.body"), modifier = Modifier.widthIn(max = 560.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.Top
            ) {
                for (destination in SupportDestination.entries) {
                    SupportLinkTile(destination) {
                        failed = runCatching { handler.openUri(destination.url) }.isFailure
                    }
                }
            }
            if (failed) Text(S.t("support.open_failed"), style = MaterialTheme.typography.bodySmall)
        }
        IknaBottomBar {
            IknaIconButton(IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
        }
    }
}

@Composable
private fun SupportLinkTile(destination: SupportDestination, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaControlShape()
    Column(
        modifier = Modifier.width(68.dp).heightIn(min = 72.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xs, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs, Alignment.CenterVertically)
    ) {
        SupportBrandMark(destination.icon)
        Text(S.t(destination.label), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}
