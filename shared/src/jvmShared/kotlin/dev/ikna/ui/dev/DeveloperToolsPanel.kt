package dev.ikna.ui.dev

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.ikna.data.dev.*
import dev.ikna.ui.session.browseUnavailableText
import dev.ikna.ui.settings.IknaSettingsToggleRow
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaWideButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One DEV surface and action contract on Android and desktop. No mock screens. */
@Composable
fun DeveloperToolsPanel(
    tools: DeveloperTools,
    applyProductLimits: Boolean,
    onOpen: (DeveloperDestination, String?) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var snapshot by remember(tools) { mutableStateOf<DeveloperDiagnostics?>(null) }
    var selectedDeck by remember(tools) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun runOperation(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        note = null
        scope.launch {
            try {
                operation()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                note = S.t("dev.tools.009") + " · " + error.toString().take(240)
            } finally {
                busy = false
            }
        }
    }
    suspend fun refresh() {
        val next = withContext(Dispatchers.IO) { tools.snapshot() }
        snapshot = next
        if (next.decks.none { it.id == selectedDeck }) selectedDeck = next.decks.firstOrNull()?.id
    }
    LaunchedEffect(tools) {
        busy = true
        try { refresh() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { note = S.t("dev.tools.009") + " · " + error.toString().take(240) }
        finally { busy = false }
    }

    Column {
        Text(S.t("dev.tools.001"), style = MaterialTheme.typography.titleMedium)
        Text(S.t("dev.tools.002"), style = MaterialTheme.typography.bodySmall)
        IknaSettingsToggleRow(
            title = S.t("dev.tools.003"), subtitle = S.t("dev.tools.004"),
            checked = applyProductLimits,
            onCheckedChange = { on -> runOperation {
                withContext(Dispatchers.IO) { tools.applyProductLimits(on) }
                refresh()
            } }
        )
        IknaWideButton(label = S.t("dev.tools.005"), enabled = !busy, onClick = { runOperation { refresh() } })
        snapshot?.let { state ->
            Text(diagnosticsText(state), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            IknaWideButton(label = S.t("dev.tools.006"), enabled = !busy, onClick = {
                clipboard.setText(AnnotatedString(diagnosticsText(state)))
                note = S.t("dev.tools.007")
            })
            Spacer(Modifier.height(8.dp))
            Text(S.t("dev.tools.010"), style = MaterialTheme.typography.labelMedium)
            state.decks.forEach { deck ->
                IknaWideButton(
                    label = (if (selectedDeck == deck.id) "✓ " else "") + deck.title,
                    filled = selectedDeck == deck.id, enabled = !busy,
                    onClick = { selectedDeck = deck.id }
                )
            }
            if (state.decks.isEmpty()) Text(S.t("dev.tools.008"), style = MaterialTheme.typography.bodySmall)
            IknaWideButton(label = S.t("dev.tools.011"), enabled = !busy, onClick = {
                onOpen(DeveloperDestination.SESSION, selectedDeck)
            })
            IknaWideButton(label = S.t("dev.tools.012"), enabled = !busy && selectedDeck != null, onClick = {
                val deck = selectedDeck ?: return@IknaWideButton
                runOperation {
                    val availability = withContext(Dispatchers.IO) { tools.checkBrowse(deck) }
                    if (availability.available) onOpen(DeveloperDestination.BROWSE, deck)
                    else note = browseUnavailableText(availability)
                }
            })
            Text(S.t("dev.tools.020"), style = MaterialTheme.typography.bodySmall)
            IknaWideButton(label = S.t("dev.tools.013"), enabled = !busy, onClick = { onOpen(DeveloperDestination.STATS, null) })
            IknaWideButton(label = S.t("dev.tools.014"), enabled = !busy, onClick = { onOpen(DeveloperDestination.CATALOG, null) })
            IknaWideButton(label = S.t("dev.tools.015"), enabled = !busy, onClick = { onOpen(DeveloperDestination.SEARCH, null) })
        }
        if (busy) Text(S.t("dev.tools.019"), style = MaterialTheme.typography.bodySmall)
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun diagnosticsText(state: DeveloperDiagnostics): String = buildString {
    appendLine("DEV · " + java.time.Instant.ofEpochMilli(state.capturedAt))
    appendLine(S.t("dev.tools.016") + ": " + state.governorReason + " · " + state.allowedNew + " / " + state.capacity)
    appendLine(S.t("dev.tools.017") + ": " + (state.planReason ?: "—") + " · " + state.pending + " / " + state.planSize)
    appendLine(S.t("dev.tools.018") + ": " + state.due + " / " + state.backlog + " · " + state.reviewRows + " · " + state.retiredCards)
    appendLine(S.t("dev.tools.021") + ": " + if (state.overrideEnabled) S.t("dev.tools.022") else S.t("dev.tools.023"))
}
