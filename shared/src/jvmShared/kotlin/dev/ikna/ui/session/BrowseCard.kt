package dev.ikna.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.ikna.domain.session.SessionCard
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.SignalFramePlacement
import dev.ikna.ui.theme.iknaSignalFrame

/**
 * One item in the passive Browse feed.
 *
 * Browse deliberately does not imitate the review session. The complete item is
 * visible at once: source-language context, pronunciation when available,
 * meaning, and provenance. Moving to another item is ordinary vertical scroll;
 * there is no reveal gesture, rating rail, or horizontal swipe contract here.
 */
@Composable
fun BrowseFeedCard(
    card: SessionCard,
    transcription: String?,
    sourceLabel: String?,
    onSource: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val sourceInteraction = remember { MutableInteractionSource() }
    val sourceFocused by sourceInteraction.collectIsFocusedAsState()
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(Space.hair, MaterialTheme.colorScheme.outline)
                .padding(horizontal = Space.lg, vertical = Space.lg)
        ) {
            Text(
                text = browseMarked(card.prompt, card.promptTarget, MaterialTheme.colorScheme.primary),
                style = browsePromptStyle(card.prompt),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (!transcription.isNullOrBlank()) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            text = card.answer,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .border(Space.hair, MaterialTheme.colorScheme.outline)
                .padding(horizontal = Space.lg, vertical = Space.lg)
        )

        if (!sourceLabel.isNullOrBlank() && onSource != null) {
            Spacer(Modifier.height(Space.md))
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .border(
                        Space.hair,
                        if (sourceFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    .hoverable(sourceInteraction)
                    .iknaSignalFrame(
                        sourceInteraction,
                        cornerRadius = 6.dp,
                        placement = SignalFramePlacement.Outer
                    )
                    .clickable(
                        interactionSource = sourceInteraction,
                        indication = null,
                        onClick = onSource
                    )
                    .padding(horizontal = Space.md, vertical = Space.sm)
            )
        }
    }
}

@Composable
private fun browsePromptStyle(prompt: String): TextStyle = when {
    prompt.length <= 42 -> MaterialTheme.typography.headlineMedium
    else -> MaterialTheme.typography.titleLarge
}

/** Same target mark as the review card, but in the compact reading layout. */
private fun browseMarked(text: String, target: IntRange?, color: Color): AnnotatedString {
    if (target == null) return AnnotatedString(text)
    val start = target.first.coerceIn(0, text.length)
    val end = (target.last + 1).coerceIn(start, text.length)
    if (end <= start) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(color = color, textDecoration = TextDecoration.Underline),
            start,
            end
        )
    }
}
