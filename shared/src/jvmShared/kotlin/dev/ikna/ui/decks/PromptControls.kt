package dev.ikna.ui.decks
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ikna.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptChoice(
	options: List<String>,
	label: (String) -> String,
	current: String,
	onPick: (String) -> Unit
) {
	// Four chips to a row was a guess about how wide a word is, and the guess
	// was wrong in Russian: «продвинутый» is wider than a quarter of the screen and the
	// last chip in the row lost its final letters inside its own border. The row
	// wraps by measured width now, so the layout is a question for the text and
	// the screen instead of a number typed here, in all three languages.
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Space.sm),
		verticalArrangement = Arrangement.spacedBy(Space.sm)
	) {
		options.forEach { option ->
			IknaChip(
				label = label(option),
				selected = current == option,
				onClick = { onPick(option) }
			)
		}
	}
}

@Composable
fun Step(text: String) {
	Row(Modifier.padding(bottom = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
		Box(Modifier.padding(top = Space.sm).size(5.dp)
			.background(MaterialTheme.colorScheme.primary, iknaTileShape()))
		Text(text = text, style = MaterialTheme.typography.bodyMedium)
	}
}
