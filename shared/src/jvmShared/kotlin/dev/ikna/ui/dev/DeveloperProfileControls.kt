package dev.ikna.ui.dev

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.data.dev.IknaDataProfile
import dev.ikna.data.dev.DeveloperScenario
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaDialog
import dev.ikna.ui.theme.IknaWideButton

/** Confirm changes before writing bootstrap state; cancellation changes nothing. */
@Composable
fun DeveloperProfileControls(
    profile: IknaDataProfile, enabled: Boolean = true,
    onSwitch: (IknaDataProfile) -> Unit
) {
    var asking by remember(profile) { mutableStateOf(false) }
    var switching by remember(profile) { mutableStateOf(false) }
    var error by remember(profile) { mutableStateOf<String?>(null) }
    val active = profile == IknaDataProfile.DEVELOPER
    Column {
        Text(S.t("dev.002"), style = MaterialTheme.typography.titleMedium)
        Text(S.t(if (active) "dev.026" else "dev.003"), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        IknaWideButton(label = S.t(if (active) "dev.006" else "dev.005"),
            enabled = enabled && !switching, onClick = { asking = true; error = null })
        if (switching) Text(S.t("dev.025"), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (asking) IknaDialog(
        title = S.t(if (active) "dev.006" else "dev.002"),
        body = S.t(if (active) "dev.024" else "dev.004"),
        confirmLabel = S.t(if (active) "dev.006" else "dev.021"),
        dismissLabel = S.t("dev.022"), onDismiss = { asking = false },
        onConfirm = {
            if (enabled && !switching) {
                asking = false
                switching = true
                try { onSwitch(if (active) IknaDataProfile.REAL else IknaDataProfile.DEVELOPER) }
                catch (failure: Exception) {
                    switching = false
                    error = S.t("dev.tools.009") + " · " + failure.toString().take(240)
                }
            }
        }
    )
}

/** A confirmed scenario is queued for startup; the live database is never reseeded here. */
@Composable
fun DeveloperScenarioControls(
    scenarioId: String, enabled: Boolean = true,
    onReseed: (DeveloperScenario) -> Unit
) {
    var selected by remember(scenarioId) { mutableStateOf(DeveloperScenario.fromId(scenarioId)) }
    var asking by remember { mutableStateOf<DeveloperScenario?>(null) }
    var restarting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column {
        Text(S.t("dev.010"), style = MaterialTheme.typography.labelMedium)
        DeveloperScenario.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { scenario ->
                    IknaWideButton(label = S.t(scenarioLabelKey(scenario)),
                        filled = selected == scenario, enabled = enabled && !restarting,
                        modifier = Modifier.weight(1f), height = 48.dp,
                        onClick = { selected = scenario })
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(S.t("dev.018"), style = MaterialTheme.typography.bodySmall)
        IknaWideButton(label = S.t("dev.017"), enabled = enabled && !restarting,
            onClick = { asking = selected; error = null })
        if (restarting) Text(S.t("dev.025"), style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    asking?.let { scenario ->
        IknaDialog(title = S.t("dev.017"),
            body = S.t("dev.018") + "\n\n" + S.t(scenarioLabelKey(scenario)),
            confirmLabel = S.t("dev.017"), dismissLabel = S.t("dev.022"),
            onDismiss = { asking = null }, onConfirm = {
                if (enabled && !restarting) {
                    asking = null
                    restarting = true
                    try { onReseed(scenario) }
                    catch (failure: Exception) {
                        restarting = false
                        error = S.t("dev.tools.009") + " · " + failure.toString().take(240)
                    }
                }
            })
    }
}

private fun scenarioLabelKey(scenario: DeveloperScenario): String = when (scenario) {
    DeveloperScenario.EMPTY -> "dev.011"
    DeveloperScenario.EARLY_HISTORY -> "dev.012"
    DeveloperScenario.MATURE_HISTORY -> "dev.013"
    DeveloperScenario.BROWSE_READY -> "dev.014"
    DeveloperScenario.RICH_STATISTICS -> "dev.015"
    DeveloperScenario.RETURN_AFTER_BREAK -> "dev.016"
}
