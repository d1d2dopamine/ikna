package dev.ikna.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.ikna.data.repo.StatsExportFormat
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.Space

@Composable
fun IknaStatsExportAction(enabled: Boolean, onSave: (Int?, StatsExportFormat) -> Unit) {
    var days by remember { mutableStateOf<Int?>(7) }
    var format by remember { mutableStateOf(StatsExportFormat.CSV) }
    IknaExpandableAction(S.t("stats.export"), enabled, onSave = { onSave(days, format) }) {
        ChoiceGroup(S.t("stats.period")) {
            for ((value, key) in listOf(7 to "study.001", 30 to "study.002", null to "stats.all")) {
                IknaChip(S.t(key), selected = days == value, onClick = { days = value })
            }
        }
        ChoiceGroup(S.t("stats.format")) {
            StatsExportFormat.entries.forEach { option ->
                IknaChip(option.name, selected = format == option, onClick = { format = option })
            }
        }
    }
}

/** An inline action with a slot for any number of independent choice groups. */
@Composable
private fun IknaExpandableAction(label: String, enabled: Boolean, onSave: () -> Unit, choices: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        IknaTextButton(label, onClick = { expanded = !expanded }, enabled = enabled)
        if (expanded && enabled) {
            choices()
            IknaTextButton(S.t("stats.save"), onClick = { expanded = false; onSave() })
        }
    }
}

@Composable
private fun ChoiceGroup(label: String, choices: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label)
        choices()
    }
}
