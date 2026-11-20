package dev.ikna.ui.decks

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ikna.data.prefs.DeckBackground
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaHomeBackdrop
import dev.ikna.ui.theme.Space
import dev.ikna.ui.theme.iknaTileShape
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IknaDeckBackgroundSettings(
    background: DeckBackground,
    onBackground: (DeckBackground) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = iknaTileShape()
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(S.t("deck.background_title"), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            DeckBackground.entries.forEach { option ->
                val selected = option == background
                Column(
                    modifier = Modifier.width(100.dp)
                        .clip(shape)
                        .border(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onBackground(option) })
                ) {
                    Box(Modifier.fillMaxWidth().height(54.dp)) { IknaHomeBackdrop(option) }
                    Text(
                        S.t("deck.background_" + option.name.lowercase(Locale.ROOT)),
                        modifier = Modifier.padding(Space.xs),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
