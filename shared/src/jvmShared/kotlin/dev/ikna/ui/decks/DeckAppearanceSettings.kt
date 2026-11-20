package dev.ikna.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.ikna.data.prefs.DeckBackground
import dev.ikna.data.prefs.DeckScreenStyle
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.Space
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IknaDeckAppearanceSettings(
    style: DeckScreenStyle,
    background: DeckBackground,
    onStyle: (DeckScreenStyle) -> Unit,
    onBackground: (DeckBackground) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(S.t("deck.screen_style"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            DeckScreenStyle.entries.forEach { option ->
                IknaChip(
                    S.t("deck.screen_" + option.name.lowercase(Locale.ROOT)),
                    selected = option == style,
                    onClick = { onStyle(option) }
                )
            }
        }
        Text(S.t("deck.background_title"), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            DeckBackground.entries.forEach { option ->
                IknaChip(
                    S.t("deck.background_" + option.name.lowercase(Locale.ROOT)),
                    selected = option == background,
                    onClick = { onBackground(option) }
                )
            }
        }
    }
}
