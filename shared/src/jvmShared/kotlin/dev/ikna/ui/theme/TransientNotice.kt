package dev.ikna.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val NOTICE_MILLIS = 5_000L

/** A short, neutral explanation pinned above the screen's bottom controls. */
@Composable
fun IknaTransientNotice(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = NOTICE_MILLIS
) {
    LaunchedEffect(message, durationMillis) {
        if (message != null) {
            delay(durationMillis.coerceAtLeast(1L))
            onDismiss()
        }
    }
    if (message == null) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, LocalIknaControlColors.current.outline)
                .clickable(
                    onClickLabel = dev.ikna.ui.text.S.t("a11y.014"),
                    role = Role.Button,
                    onClick = onDismiss
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
