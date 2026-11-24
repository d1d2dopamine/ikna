package dev.ikna.ui.catalog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.data.catalog.*
import dev.ikna.data.prefs.IknaAppearanceVariant
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Catalogue v2 collection selector: Everyday, Knowledge, World.
 *
 * Uses Ikna's gliding underline strip on open canvas rather than stacked cards
 * or pill wrappers, with a single quiet description line for the active tab.
 */
@Composable
fun IknaCatalogCollectionTabs(
	selectedCollection: String,
	counts: Map<String, Int>,
	onSelect: (String) -> Unit
) {
	val colors = MaterialTheme.colorScheme
	val animations = LocalIknaMotionEnabled.current
	val rounded = LocalIknaAppearanceVariant.current == IknaAppearanceVariant.ROUNDED
	val sound = LocalIknaSoundFeedback.current
	val spots = remember { mutableStateMapOf<String, IntRange>() }
	val active = selectedCollection.ifEmpty { COLLECTION_EVERYDAY }
	val activeSpot = spots[active]
	val targetLeft = activeSpot?.first?.toFloat() ?: 0f
	val targetRight = activeSpot?.last?.toFloat() ?: 0f
	val hasSpot = activeSpot != null
	val animLeft = remember { Animatable(0f) }
	val animRight = remember { Animatable(0f) }
	var indicatorReady by remember { mutableStateOf(false) }

	LaunchedEffect(targetLeft, targetRight, hasSpot, animations) {
		if (!hasSpot) return@LaunchedEffect
		if (!indicatorReady || !animations) {
			animLeft.snapTo(targetLeft)
			animRight.snapTo(targetRight)
			indicatorReady = true
		} else {
			val spec = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
			launch { animLeft.animateTo(targetLeft, spec) }
			launch { animRight.animateTo(targetRight, spec) }
		}
	}

	Column(modifier = Modifier.fillMaxWidth()) {
		Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
			Box {
				Row(
					horizontalArrangement = Arrangement.spacedBy(22.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					CATALOG_COLLECTIONS.forEach { collectionId ->
						val here = collectionId == active
						val count = counts[collectionId] ?: 0
						val interaction = remember { MutableInteractionSource() }
						val hovered by interaction.collectIsHoveredAsState()
						val pressed by interaction.collectIsPressedAsState()
						val scale = iknaPressScale(pressed, animations, pressedScale = 0.96f)
						val labelColor by animateColorAsState(
							targetValue = when {
								here -> colors.onBackground
								hovered -> colors.onBackground.copy(alpha = 0.85f)
								else -> colors.onSurfaceVariant
							},
							animationSpec = if (animations) tween(Motion.controlChangeDurationMillis) else snap(),
							label = "catalog-collection-label"
						)
						Row(
							modifier = Modifier
								.onGloballyPositioned { coords ->
									val x = coords.parentLayoutCoordinates
										?.localPositionOf(coords, Offset.Zero)
										?.x
										?: 0f
									val left = x.roundToInt()
									spots[collectionId] = left..(left + coords.size.width)
								}
								.heightIn(min = 42.dp)
								.graphicsLayer {
									scaleX = scale
									scaleY = scale
								}
								.semantics { selected = here }
								.hoverable(interaction)
								.clickable(
									interactionSource = interaction,
									indication = null,
									role = Role.Tab
								) {
									sound.play(IknaSoundCue.TAP)
									onSelect(collectionId)
								}
								.padding(horizontal = 2.dp, vertical = 10.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Text(
								text = catalogCollectionTitle(collectionId),
								style = MaterialTheme.typography.bodyMedium,
								fontWeight = if (here) FontWeight.SemiBold else FontWeight.Medium,
								color = labelColor,
								maxLines = 1
							)
							if (count > 0) {
								Text(
									text = count.toString(),
									style = iknaNumberStyle(MaterialTheme.typography.labelSmall),
									color = if (here) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.72f),
									maxLines = 1
								)
							}
						}
					}
				}
				Canvas(modifier = Modifier.matchParentSize()) {
					if (indicatorReady && animRight.value > animLeft.value) {
						val barHeight = 2.dp.toPx()
						val corner = CornerRadius(if (rounded) barHeight / 2f else 0f)
						drawRoundRect(
							color = colors.primary,
							topLeft = Offset(animLeft.value, size.height - barHeight),
							size = Size(animRight.value - animLeft.value, barHeight),
							cornerRadius = corner
						)
					}
				}
			}
		}
		IknaRule()
		Spacer(Modifier.height(Space.sm))
		Text(
			text = catalogCollectionDescription(active),
			style = MaterialTheme.typography.bodySmall,
			color = colors.onSurfaceVariant
		)
	}
}

/** Calm empty state for Catalogue v2 collections that are not published yet. */
@Composable
fun IknaCatalogCollectionEmptyState(
	collection: String,
	onOpenEveryday: () -> Unit
) {
	val colors = MaterialTheme.colorScheme
	val titleKey = if (collection == COLLECTION_WORLD) "cat.052" else "cat.050"
	val bodyKey = if (collection == COLLECTION_WORLD) "cat.053" else "cat.051"

	Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.md)) {
		Text(
			text = S.t(titleKey),
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.SemiBold,
			color = colors.onBackground
		)
		Spacer(Modifier.height(Space.xs))
		Text(
			text = S.t(bodyKey),
			style = MaterialTheme.typography.bodyMedium,
			color = colors.onSurfaceVariant
		)
		Spacer(Modifier.height(Space.md))
		IknaTextButton(
			label = S.t("cat.054"),
			onClick = onOpenEveryday,
			color = colors.primary
		)
	}
}

private fun catalogCollectionTitle(collectionId: String): String = when (collectionId) {
	COLLECTION_KNOWLEDGE -> S.t("cat.045")
	COLLECTION_WORLD -> S.t("cat.046")
	else -> S.t("cat.044")
}

private fun catalogCollectionDescription(collectionId: String): String = when (collectionId) {
	COLLECTION_KNOWLEDGE -> S.t("cat.048")
	COLLECTION_WORLD -> S.t("cat.049")
	else -> S.t("cat.047")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IknaCatalogDeckRow(
	deck: CatalogDeck,
	resultNote: String? = null,
	planRefreshFailed: Boolean = false,
	onRetryPlan: () -> Unit = {},
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
	val contexts = if (deck.contextCount > deck.chunkCount) {
		"  \u00B7  " + deck.contextCount + " " + S.t("cat.055")
	} else ""

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
				FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
					val label = MaterialTheme.typography.labelMedium
					Row(horizontalArrangement = Arrangement.spacedBy(Space.xs),
						verticalAlignment = Alignment.CenterVertically) {
						Text(deck.lang.uppercase(), style = label, color = muted)
						IknaGlyphIcon(IknaGlyph.FORWARD, muted, size = 10.dp)
						Text(deck.meaningLang.uppercase(), style = label, color = muted)
					}
					Text(
						text = deck.chunkCount.toString() + " " + S.t("cat.015") +
							contexts +
							(if (size != null) "  \u00B7  " + size + " " + S.t("cat.024") else ""),
						style = label,
						color = muted
					)
				}
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
				if (open) {
					IknaTextButton(
						label = S.t("cat.035"),
						onClick = onInstall,
						enabled = !blocked,
						color = muted
					)
				}
			}
			if (open && blocked && !busy && !previewLoading) {
				// The row itself is quiet, so the only reason this button is off is
				// another deck's download; say so instead of leaving a dead button.
				Spacer(Modifier.height(Space.xs))
				Text(
					text = S.t("cat.042"),
					style = MaterialTheme.typography.labelMedium,
					color = muted
				)
			}
		} else if (open) {
			// Download is offered only below the visible licence and attribution.
			Spacer(Modifier.height(Space.md))
			IknaWideButton(
				label = S.t("cat.018"),
				filled = open,
				enabled = !blocked,
				height = 52.dp,
				onClick = onInstall
			)
			if (blocked && !busy && !previewLoading) {
				Spacer(Modifier.height(Space.xs))
				Text(
					text = S.t("cat.042"),
					style = MaterialTheme.typography.labelMedium,
					color = muted
				)
			}
		}
		if (planRefreshFailed) {
			Spacer(Modifier.height(Space.md))
			Text(S.t("cat.043"), style = MaterialTheme.typography.bodyMedium, color = ink,
				modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
			IknaTextButton(label = S.t("load.002"), onClick = onRetryPlan, enabled = !blocked)
		}
		resultNote?.let { note ->
			Spacer(Modifier.height(Space.md))
			Text(
				text = note,
				style = MaterialTheme.typography.bodyMedium,
				color = ink,
				modifier = Modifier.fillMaxWidth()
					.semantics { liveRegion = LiveRegionMode.Polite }
					.background(MaterialTheme.colorScheme.surface)
					.padding(Space.md)
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
			.background(MaterialTheme.colorScheme.surface, dev.ikna.ui.theme.iknaCardShape())
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
