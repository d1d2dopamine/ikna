package dev.ikna.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaWordmark

/**
 * The first welcome title says "This is ikna." with a genuinely text-sized
 * wordmark in the sentence. Later slides keep the large centred mark they had
 * before: only the first slide replaces it with the inline introduction.
 */
@Composable
fun IknaOnboardingTitle(
    titleKey: String,
    branded: Boolean,
    modifier: Modifier = Modifier
) {
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium)
    val color = MaterialTheme.colorScheme.onBackground
    if (!branded) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IknaWordmark(height = 44.dp, label = "ikna")
            Spacer(Modifier.height(16.dp))
            Text(
                text = S.t(titleKey),
                style = style,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = S.t(titleKey),
            style = style,
            color = color
        )
        Spacer(Modifier.width(6.dp))
        IknaWordmark(height = 22.dp, label = "ikna")
        Text(text = ".", style = style, color = color)
    }
}
