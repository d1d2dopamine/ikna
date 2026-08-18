package dev.ikna.ui.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space

/**
 * The only screen that can appear while card state is between schedulers.
 *
 * It deliberately has no navigation and no cancel action. The review log and
 * decks are already safe; opening a session before the derived card table has
 * committed is the one operation that would make the migration ambiguous.
 */
@Composable
fun SchedulerMigrationScreen(failed: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(Edge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "ikna",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Space.xl))
        Text(
            text = if (failed) S.t("mig.003") else S.t("mig.001"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            text = if (failed) S.t("mig.004") else S.t("mig.002"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (failed) {
            Spacer(Modifier.height(Space.lg))
            IknaWideButton(
                label = S.t("mig.005"),
                filled = true,
                onClick = onRetry
            )
        }
    }
}
