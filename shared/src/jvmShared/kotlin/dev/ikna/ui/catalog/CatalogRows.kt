package dev.ikna.ui.catalog
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.ikna.data.catalog.*

@Composable
fun IknaCatalogDeckRow(
	deck: CatalogDeck,
	open: Boolean,
	installed: Boolean,
	busy: Boolean,
	blocked: Boolean,
	importing: Boolean,
	fraction: Float,
	percent: Int,
	previewLoading: Boolean,
	previewFailed: Boolean,
	previewCards: List<CatalogPreviewCard>,
	onOpen: () -> Unit,
	onPreview: () -> Unit,
	onSource: (String) -> Unit,
	onInstall: () -> Unit
) {
	val ink = MaterialTheme.colorScheme.onBackground
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	val size = catalogSize(deck.sizeBytes)

	Column(modifier = Modifier.fillMaxWidth()) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = deck.title,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
					style = MaterialTheme.typography.bodyLarge,
					color = ink
				)
				Spacer(Modifier.height(Space.xs))
				Text(
					text = deck.lang.uppercase() + " \u2192 " + deck.meaningLang.uppercase() +
						"  \u00B7  " + deck.chunkCount + " " + S.t("cat.015") +
						(if (size != null) "  \u00B7  " + size + " " + S.t("cat.024") else ""),
					style = MaterialTheme.typography.labelMedium,
					color = muted
				)
			}
			IknaTextButton(
				label = if (open) S.t("add.068") else S.t("add.067"),
				onClick = onOpen,
				color = muted
			)
		}

		if (open) {
			Spacer(Modifier.height(Space.sm))
			Text(
				text = S.t("cat.016") + deck.licence,
				style = MaterialTheme.typography.bodySmall,
				color = ink
			)
			if (deck.attribution.isNotEmpty()) {
				Spacer(Modifier.height(Space.xs))
				Text(
					text = deck.attribution,
					style = MaterialTheme.typography.bodySmall,
					color = muted
				)
			}
			if (deck.sources.isNotEmpty()) {
				Spacer(Modifier.height(Space.xs))
				Text(
					text = S.t("cat.017") + deck.sources.joinToString(", "),
					style = MaterialTheme.typography.bodySmall,
					color = muted
				)
			}
			if (deck.phonetics) {
				// Said here and not as a badge on the collapsed row: it is a
				// reason to prefer one deck over another, but not a reason to
				// make every row taller for the people who will never use it.
				// Absent rather than negated when a deck has none, because
				// "no pronunciation" on two hundred rows is noise.
				Spacer(Modifier.height(Space.xs))
				Text(
					text = S.t("cat.041"),
					style = MaterialTheme.typography.bodySmall,
					color = muted
				)
			}
			if (licenceIsShareAlike(deck.licence)) {
				// Said here because it is the one clause that reaches somebody who
				// does more than study: a deck built out of this one and handed on
				// carries the same terms. Studying it obliges nobody to anything.
				Spacer(Modifier.height(Space.xs))
				Text(
					text = S.t("cat.025"),
					style = MaterialTheme.typography.labelMedium,
					color = muted
				)
			}

			Spacer(Modifier.height(Space.md))
			when {
				previewLoading -> Text(
					text = S.t("cat.037"),
					style = MaterialTheme.typography.labelLarge,
					color = muted
				)

				previewFailed -> IknaTextButton(
					label = S.t("cat.038"),
					onClick = onPreview,
					color = muted
				)

				previewCards.isEmpty() -> IknaTextButton(
					label = S.t("cat.036"),
					onClick = onPreview,
					color = muted
				)

				else -> {
					Text(
						text = S.t("cat.039"),
						style = MaterialTheme.typography.labelMedium,
						color = muted
					)
					previewCards.forEachIndexed { index, card ->
						Spacer(Modifier.height(Space.md))
						IknaCatalogPreviewCard(card = card, number = index + 1, onSource = onSource)
					}
				}
			}
		}

		if (busy) {
			Spacer(Modifier.height(Space.md))
			Text(
				text = if (importing) S.t("cat.023") else S.t("cat.019") + "  " + percent + "%",
				style = MaterialTheme.typography.labelLarge,
				color = ink
			)
			Spacer(Modifier.height(Space.sm))
			IknaProgress(
				fraction = if (importing) 1f else fraction,
				height = 6.dp,
				color = ink,
				track = true
			)
		} else if (installed) {
			// Already on the phone, said on the row rather than found out by
			// downloading the same file twice. The way to fetch a fresh copy is
			// next to it, small, because wanting one is the rarer case: a deck
			// that is already installed is replaced by its own identifier, so
			// nothing splits in two and no history is lost.
			Spacer(Modifier.height(Space.md))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					text = S.t("cat.034"),
					style = MaterialTheme.typography.labelLarge,
					color = muted,
					modifier = Modifier.weight(1f)
				)
				IknaTextButton(
					label = S.t("cat.035"),
					onClick = onInstall,
					enabled = !blocked,
					color = muted
				)
			}
		} else {
			Spacer(Modifier.height(Space.md))
			IknaWideButton(
				label = S.t("cat.018"),
				filled = open,
				enabled = !blocked,
				height = 52.dp,
				onClick = onInstall
			)
		}
	}
}

@Composable
fun IknaCatalogPreviewCard(
	card: CatalogPreviewCard,
	number: Int,
	onSource: (String) -> Unit
) {
	val muted = MaterialTheme.colorScheme.onSurfaceVariant
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.padding(Space.md)
	) {
		Text(
			text = S.t("cat.040") + number + "  ·  " + card.text,
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.onBackground
		)
		Spacer(Modifier.height(Space.xs))
		Text(
			text = card.context,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onBackground
		)
		Spacer(Modifier.height(Space.xs))
		Text(
			text = card.translation,
			style = MaterialTheme.typography.bodySmall,
			color = muted
		)
		card.tatoebaId?.let { id ->
			Spacer(Modifier.height(Space.xs))
			Text(
				text = S.t("src.001") + "Tatoeba #" + id,
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable { onSource(id) }
			)
		}
	}
}
