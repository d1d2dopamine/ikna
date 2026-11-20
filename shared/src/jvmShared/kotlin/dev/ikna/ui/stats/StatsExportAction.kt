package dev.ikna.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import dev.ikna.data.repo.StatsExportFormat
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaExpandableAction
import dev.ikna.ui.theme.Space

@Composable
fun IknaStatsExportAction(enabled: Boolean, onSave: (Int?, StatsExportFormat) -> Unit) {
    var days by remember { mutableStateOf<Int?>(7) }
    var format by remember { mutableStateOf(StatsExportFormat.CSV) }
    IknaExpandableAction(S.t("stats.export"), S.t("stats.save"), enabled, onConfirm = { onSave(days, format) }) {
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

@Composable
private fun ChoiceGroup(label: String, choices: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label)
        choices()
    }
}
