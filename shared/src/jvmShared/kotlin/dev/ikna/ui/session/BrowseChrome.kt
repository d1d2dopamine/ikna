package dev.ikna.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaSpark
import dev.ikna.ui.theme.Motion
import dev.ikna.ui.theme.Space

/** Quiet context only; Browse has no task counter or success state. */
@Composable
fun IknaBrowseTopBar(deckTitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = S.t("browse.001"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = deckTitle.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun IknaBrowseEmptyState(hadCards: Boolean, animations: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hadCards) {
            val appear = remember { Animatable(if (animations) 0f else 1f) }
            LaunchedEffect(animations) {
                if (animations) appear.animateTo(1f, Motion.reveal) else appear.snapTo(1f)
            }
            IknaSpark(
                color = MaterialTheme.colorScheme.primary,
                size = 112.dp,
                progress = appear.value
            )
            Spacer(Modifier.height(Space.lg))
        }
        Text(
            text = S.t(if (hadCards) "browse.003" else "browse.006"),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.md))
        Text(
            text = S.t(if (hadCards) "browse.004" else "browse.007"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
