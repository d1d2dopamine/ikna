package dev.ikna.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaScrollableColumn
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.Space

@Composable
fun IknaCardLoadFailure(browse: Boolean = false, onRetry: () -> Unit) {
    IknaScrollableColumn(
        Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically)
    ) {
        Text(
            S.t(if (browse) "load.003" else "load.001"),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        IknaTextButton(S.t("load.002"), onClick = onRetry)
    }
}
