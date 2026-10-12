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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val accessibility = LocalAccessibilityManager.current
    val timeout = noticeTimeoutMillis(durationMillis, accessibility)
    val dismiss = rememberUpdatedState(onDismiss)
    LaunchedEffect(message, timeout) {
        if (message != null) {
            delay(timeout)
            dismiss.value()
        }
    }
    if (message == null) return

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .clip(iknaControlShape())
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, LocalIknaControlColors.current.outline, iknaControlShape())
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

/** Honor accessibility's reading time without shortening the ordinary duration. */
internal fun noticeTimeoutMillis(requested: Long, accessibility: AccessibilityManager?): Long {
    val minimum = requested.coerceAtLeast(1L)
    val recommended = accessibility?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = minimum,
        containsIcons = false,
        containsText = true,
        containsControls = true
    ) ?: minimum
    return recommended.coerceAtLeast(minimum)
}
