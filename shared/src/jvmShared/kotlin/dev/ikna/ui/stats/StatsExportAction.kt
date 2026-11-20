package dev.ikna.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ikna.data.repo.StatsExportFormat
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaAnchoredAction
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaTileShape

@Composable
fun IknaStatsExportAction(enabled: Boolean, onSave: (Int?, StatsExportFormat) -> Unit) {
    var days by remember { mutableStateOf<Int?>(7) }
    var format by remember { mutableStateOf(StatsExportFormat.CSV) }
    IknaAnchoredAction(S.t("stats.export"), S.t("stats.save"), enabled, onConfirm = { onSave(days, format) }) {
        ChoiceGroup(S.t("stats.period")) {
            for ((value, key) in listOf(7 to "study.001", 30 to "study.002", null to "stats.all")) {
                Choice(S.t(key), selected = days == value, onClick = { days = value })
            }
        }
        ChoiceGroup(S.t("stats.format")) {
            StatsExportFormat.entries.forEach { option ->
                Choice(option.name, selected = format == option, onClick = { format = option })
            }
        }
    }
}

@Composable
private fun ChoiceGroup(label: String, choices: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = iknaTileShape()) {
        Column(Modifier.selectableGroup().padding(Space.sm)) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(Space.xs))
            choices()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
