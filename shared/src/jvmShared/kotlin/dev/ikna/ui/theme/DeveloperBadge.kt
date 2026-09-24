package dev.ikna.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S

/** Small persistent marker so a developer never mistakes synthetic data for real learning. */
@Composable
fun IknaDeveloperBadge(modifier: Modifier = Modifier) {
    Text(
        text = S.t("dev.001"),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}
