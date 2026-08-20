package dev.ikna.ui.decks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.ikna.AppContainer
import dev.ikna.data.anki.AnkiImportError
import dev.ikna.data.anki.AnkiImportResult
import dev.ikna.data.anki.AnkiImportState
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaPanel
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space

/** The visible, deliberately non-destructive side of Anki Bridge. */
@Composable
fun AnkiImportScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val state by container.ankiImport.state.collectAsState()
    var lang by remember { mutableStateOf("en") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) container.ankiImport.start(uri, lang)
    }
    val choose: () -> Unit = {
        container.ankiImport.reset()
        picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Edge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IknaIconButton(
                glyph = IknaGlyph.BACK,
                onClick = onBack,
                label = S.t("a11y.001")
            )
            Text(
                text = S.t("anki.001"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = Space.sm)
            )
        }
        IknaRule()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Edge),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            IknaPanel {
                Text(S.t("anki.002"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    S.t("anki.003"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // The accent colour was doing the job of an alarm here. This is
                // a plain fact about how the schedule is rebuilt, not a warning.
                Text(
                    S.t("anki.028"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                S.t("anki.004"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            LangChips(current = lang, onPick = { lang = it })

            when (val current = state) {
                AnkiImportState.Idle -> {
                    IknaWideButton(label = S.t("anki.005"), onClick = choose, filled = true)
                }
                AnkiImportState.Running -> {
                    Text(
                        S.t("anki.006"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(S.t("anki.007"), style = MaterialTheme.typography.bodyMedium)
                }
                is AnkiImportState.Done -> {
                    ResultSummary(current.result)
                    IknaWideButton(label = S.t("anki.017"), onClick = choose)
                    IknaWideButton(label = S.t("anki.016"), onClick = onBack, filled = true)
                }
                is AnkiImportState.Failed -> {
                    IknaPanel {
                        Text(
                            errorText(current.error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(S.t("anki.024"), style = MaterialTheme.typography.bodyMedium)
                    }
                    IknaWideButton(label = S.t("anki.017"), onClick = choose, filled = true)
                }
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun ResultSummary(result: AnkiImportResult) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(S.t("anki.025"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Metric(S.t("anki.008"), result.decks)
        Metric(S.t("anki.009"), result.cards)
        Metric(S.t("anki.010"), result.reviewEventsImported)
        if (result.reviewEventsSkipped > 0) Metric(S.t("anki.011"), result.reviewEventsSkipped)
        if (result.suspendedOrBuried > 0) Metric(S.t("anki.012"), result.suspendedOrBuried)
        if (result.skippedCards > 0) Metric(S.t("anki.013"), result.skippedCards)
        if (result.mediaCards > 0) Metric(S.t("anki.014"), result.mediaCards)
        if (result.fallbackCards > 0) Metric(S.t("anki.030"), result.fallbackCards)
        if (result.historyWasLimited) Text(S.t("anki.015"), color = MaterialTheme.colorScheme.primary)
        Text(S.t("anki.029"), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Metric(label: String, value: Int) {
    Text(label + value, style = MaterialTheme.typography.bodyMedium)
}

private fun errorText(error: AnkiImportError): String = when (error) {
    AnkiImportError.FILE_TOO_LARGE -> S.t("anki.022")
    AnkiImportError.NOT_APKG -> S.t("anki.018")
    AnkiImportError.NO_COLLECTION -> S.t("anki.019")
    AnkiImportError.UNSUPPORTED_COLLECTION -> S.t("anki.020")
    AnkiImportError.UNREADABLE_DATABASE -> S.t("anki.021")
    AnkiImportError.NO_USABLE_CARDS -> S.t("anki.023")
    AnkiImportError.FAILED -> S.t("anki.018")
}
