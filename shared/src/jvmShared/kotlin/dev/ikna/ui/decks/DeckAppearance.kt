package dev.ikna.ui.decks
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.ikna.data.prefs.DeckLook
import dev.ikna.data.prefs.NO_TINT

@Composable
fun IknaDeckAppearance(look: DeckLook, onChange: (String, Int) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

Text(
    text = S.t("look.001"),
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Medium
)
Spacer(Modifier.height(Space.xs))
Text(
    text = S.t("look.002"),
    style = MaterialTheme.typography.bodySmall,
    color = muted
)
Spacer(Modifier.height(Space.md))

// Two characters, typed, rather than a grid of emoji.
//
// The grid was here until 0.2.0 and it was the wrong offer: the
// phone draws emoji in its own full-colour style, which sits on
// this app's flat marks like a sticker on a blueprint. A field
// is also smaller than the grid it replaces, and it accepts the
// things people actually want in that square -- initials, a
// language pair, a number -- none of which a fixed set of
// pictures could have guessed.
//
// Left empty, the square keeps working out its own letters, so
// there is nothing to undo and no third state to explain.
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Space.md)
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .border(Space.hair, MaterialTheme.colorScheme.outline),
        contentAlignment = Alignment.Center
    ) {
        BasicTextField(
            value = look.label,
            onValueChange = { typed ->
                onChange(typed, look.tint)
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.fillMaxWidth()
        )
    }
    Text(
        text = S.t("look.004"),
        style = MaterialTheme.typography.bodySmall,
        color = muted
    )
}

Spacer(Modifier.height(Space.sm))
Text(
    text = S.t("look.003"),
    style = MaterialTheme.typography.bodySmall,
    color = muted
)
Spacer(Modifier.height(Space.sm))

// Eight fixed colours, no colour picker. The square has to stay
// legible against twelve palettes in two lighting modes, and a
// free hex field is one slider away from a deck nobody can see.
Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
    DeckTints.forEachIndexed { index, colour ->
        val picked = look.tint == index
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(colour)
                .border(
                    if (picked) 2.dp else Space.hair,
                    if (picked) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.outline
                )
                .clickable {
                    onChange(look.label, if (picked) NO_TINT else index)
                }
        )
    }
}

Spacer(Modifier.height(Space.lg))
IknaRule()
Spacer(Modifier.height(Space.lg))


}
