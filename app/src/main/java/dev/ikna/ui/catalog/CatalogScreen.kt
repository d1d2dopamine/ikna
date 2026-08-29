package dev.ikna.ui.catalog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ikna.AppContainer
import dev.ikna.data.catalog.CATALOG_PAGE_URL
import dev.ikna.data.catalog.CatalogDeck
import dev.ikna.data.catalog.CatalogFetch
import dev.ikna.data.catalog.CatalogFilter
import dev.ikna.data.catalog.CatalogIndex
import dev.ikna.data.catalog.CatalogPreviewCard
import dev.ikna.data.catalog.TIER_FULL
import dev.ikna.data.catalog.catalogPackId
import dev.ikna.data.catalog.catalogSize
import dev.ikna.data.catalog.decksFor
import dev.ikna.data.catalog.learnableLangs
import dev.ikna.data.catalog.levelsFor
import dev.ikna.data.catalog.licenceIsShareAlike
import dev.ikna.data.catalog.meaningLangsFor
import dev.ikna.data.catalog.progressFractionOf
import dev.ikna.data.catalog.progressPercentOf
import dev.ikna.data.catalog.subjectsFor
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.catalog.tierOf
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.BarHeight
import dev.ikna.ui.theme.Edge
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaBottomBar
import dev.ikna.ui.theme.IknaGlyph
import dev.ikna.ui.theme.IknaIconButton
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaProgress
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import dev.ikna.ui.update.installedVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Decks somebody else already built, offered by language pair.
 *
 * Until this version there were exactly two ways to get a deck: the two that
 * ship inside the app, and a model asked to write one. The second is the reason
 * the prompt screen exists and it works, but what comes back is a model's idea of
 * a phrase and its meaning, checked by nobody. This screen is the other kind of
 * source: sentences written by people, and word senses written by people, taken
 * out of two open corpora that state their licence -- Tatoeba and Wiktionary,
 * through Wiktextract. docs/SOURCES.md is why those two and not AnkiWeb.
 *
 * Three things about how it works are deliberate and worth stating here, because
 * they are what keep the app's promises intact:
 *
 * - Nothing is built on the phone. The cutting, the sieving and the numbering
 *   happen once, in CI, in tools/catalog; what the phone downloads is a finished
 *   deck in the format the importer already reads, and it goes in through the
 *   same path a deck sent by a friend does.
 * - The licence is shown before the download, not after it. It is on the row that
 *   is tapped, and the line that names who is being credited travels inside the
 *   deck's own cards, so it survives an export and a phone being replaced.
 * - Nothing about the person goes out. Every request is a GET for a static file:
 *   the index, an optional bounded preview, or the deck somebody pressed. There
 *   is no account or identifier, and nothing is fetched in the background.
 *
 * How much there is for a given pair is not decided here. The pipeline measures
 * it and says "full" or "thin" in the index, and this screen repeats that, because
 * a promise about deck size made in an app is a promise about somebody else's
 * corpus.
 */
@Composable
fun CatalogScreen(
	container: AppContainer,
	onBack: () -> Unit
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()

	var index by remember { mutableStateOf<CatalogIndex?>(null) }
	var loading by remember { mutableStateOf(true) }
	var filter by remember { mutableStateOf(CatalogFilter()) }

	// Which row is open, which row is downloading, and how far it has got. One
	// at a time on purpose: two decks arriving at once share a progress band
	// nobody can read, and the database would be written by both.
	var openId by remember { mutableStateOf<String?>(null) }
	var busyId by remember { mutableStateOf<String?>(null) }
	var importing by remember { mutableStateOf(false) }
	var readBytes by remember { mutableStateOf(0L) }
	var totalBytes by remember { mutableStateOf(0L) }
	var note by remember { mutableStateOf<String?>(null) }
	var installed by remember { mutableStateOf(emptySet<String>()) }
	var previewId by remember { mutableStateOf<String?>(null) }
	var previewLoading by remember { mutableStateOf(false) }
	var previewFailed by remember { mutableStateOf(false) }
	var previewCards by remember { mutableStateOf<List<CatalogPreviewCard>>(emptyList()) }
	var previewToken by remember { mutableStateOf(0) }

	// Which of these decks the phone already has. Read from the deck table
	// each time this screen opens rather than remembered inside it: a deck
	// deleted on the decks screen has to become downloadable again, and a
	// deck installed here has to stop being offered the moment it lands.
	suspend fun refreshInstalled() {
		installed = withContext(Dispatchers.IO) {
			runCatching { container.deckRepository.decks().map { it.id }.toSet() }
				.getOrDefault(emptySet())
		}
	}

	suspend fun load() {
		loading = true
		note = null
		val fetched = CatalogFetch(installedVersion(context)).index()
		index = fetched
		loading = false
		if (fetched == null) return
		// The meanings default to the language the app itself is in, because
		// somebody reading this screen in Polish is not learning from Russian
		// glosses. The language being learned is never guessed: it is the one
		// question only the person can answer.
		val meanings = meaningLangsFor(fetched, "")
		filter = filter.copy(
			meaningLang = if (meanings.contains(S.lang)) S.lang else ""
		)
	}

	LaunchedEffect(Unit) {
		refreshInstalled()
		load()
	}

	fun install(deck: CatalogDeck) {
		if (busyId != null || previewLoading) return
		scope.launch {
			busyId = deck.id
			importing = false
			note = null
			readBytes = 0L
			totalBytes = deck.sizeBytes
			val text = CatalogFetch(installedVersion(context)).deck(deck) { read, total ->
				readBytes = read
				if (total > 0L) totalBytes = total
			}
			if (text == null) {
				busyId = null
				note = S.t("cat.020")
				return@launch
			}
			// The same importer a shared deck file goes through, with the deck's
			// own identifier so downloading it twice replaces it instead of
			// leaving two copies with one history split between them.
			importing = true
			val result = withContext(Dispatchers.IO) {
				runCatching {
					container.packLoader.importJsonl(
						packId = catalogPackId(deck.id),
						title = deck.title,
						lang = deck.lang,
						text = text
					)
				}.getOrNull()
			}
			// A deck added now is wanted now: the day was planned before it
			// existed, so the plan is dropped and built again.
			if (result != null && result.installed > 0) {
				container.learningRepository.invalidatePlan()
			}
			refreshInstalled()
			importing = false
			busyId = null
			note = if (result == null || result.installed == 0) {
				S.t("cat.021")
			} else {
				S.t("cat.022") + result.installed
			}
		}
	}

	fun preview(deck: CatalogDeck) {
		if (busyId != null || (previewLoading && previewId == deck.id)) return
		val token = previewToken + 1
		previewToken = token
		previewId = deck.id
		previewLoading = true
		previewFailed = false
		previewCards = emptyList()
		scope.launch {
			val cards = CatalogFetch(installedVersion(context)).preview(deck)
			// A slow answer for a row that has since been closed must not appear
			// under the next row the person opened.
			if (previewToken != token || previewId != deck.id) return@launch
			previewCards = cards.orEmpty()
			previewFailed = cards == null
			previewLoading = false
		}
	}

	val ink = MaterialTheme.colorScheme.onBackground
	val muted = MaterialTheme.colorScheme.onSurfaceVariant

	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize().padding(bottom = BarHeight)) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.height(BarHeight)
					.padding(horizontal = Space.sm),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = S.t("cat.001"),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					modifier = Modifier.padding(start = Space.xs)
				)
			}

			Column(
				modifier = Modifier
					.weight(1f)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = Edge)
			) {
				Spacer(Modifier.height(Space.md))
				Text(
					text = S.t("cat.002"),
					style = MaterialTheme.typography.bodyMedium
				)

				val list = index
				if (loading) {
					Spacer(Modifier.height(Space.lg))
					Text(
						text = S.t("cat.003"),
						style = MaterialTheme.typography.labelLarge,
						color = ink
					)
				} else if (list == null) {
					// A list that did not arrive is not an error to be explained away:
					// the same page can be read in a browser, and the deck that ships
					// with the app is still there.
					Spacer(Modifier.height(Space.lg))
					Text(
						text = S.t("cat.004"),
						style = MaterialTheme.typography.bodyMedium,
						color = muted
					)
					Spacer(Modifier.height(Space.md))
					IknaWideButton(
						label = S.t("cat.006"),
						filled = true,
						onClick = { scope.launch { load() } }
					)
					Spacer(Modifier.height(Space.sm))
					IknaWideButton(
						label = S.t("cat.005"),
						onClick = { openPage(context) }
					)
				} else {
					Spacer(Modifier.height(Space.lg))
					IknaRule()
					Spacer(Modifier.height(Space.lg))

					Text(
						text = S.t("cat.007"),
						style = MaterialTheme.typography.labelMedium,
						color = muted
					)
					Spacer(Modifier.height(Space.sm))
					CodeChips(
						options = learnableLangs(list),
						current = filter.lang,
						onPick = { picked ->
							// Changing what is being learned drops the narrower answers:
							// a topic that existed for one language rarely exists for the
							// next, and a filter nobody can see is a list that looks empty.
							filter = filter.copy(lang = picked, subject = "", level = "")
							openId = null
						}
					)

					Spacer(Modifier.height(Space.md))
					Text(
						text = S.t("cat.008"),
						style = MaterialTheme.typography.labelMedium,
						color = muted
					)
					Spacer(Modifier.height(Space.sm))
					CodeChips(
						options = meaningLangsFor(list, filter.lang),
						current = filter.meaningLang,
						onPick = { picked ->
							filter = filter.copy(meaningLang = picked, subject = "", level = "")
							openId = null
						}
					)

					val subjects = subjectsFor(list, filter)
					if (subjects.isNotEmpty()) {
						Spacer(Modifier.height(Space.md))
						Text(
							text = S.t("cat.009"),
							style = MaterialTheme.typography.labelMedium,
							color = muted
						)
						Spacer(Modifier.height(Space.sm))
						PickChips(
							options = subjects,
							label = { it },
							current = filter.subject,
							onPick = { picked -> filter = filter.copy(subject = picked) }
						)
					}

					val levels = levelsFor(list, filter)
					if (levels.isNotEmpty()) {
						Spacer(Modifier.height(Space.md))
						Text(
							text = S.t("cat.010"),
							style = MaterialTheme.typography.labelMedium,
							color = muted
						)
						Spacer(Modifier.height(Space.sm))
						PickChips(
							options = levels,
							label = { levelLabel(it) },
							current = filter.level,
							onPick = { picked -> filter = filter.copy(level = picked) }
						)
					}

					// What the pipeline measured for this pair, said before the list
					// rather than discovered by scrolling it.
					val tier = tierOf(list, filter.lang, filter.meaningLang)
					if (filter.lang.isNotEmpty() && filter.meaningLang.isNotEmpty()) {
						Spacer(Modifier.height(Space.md))
						Text(
							text = when (tier) {
								TIER_FULL -> S.t("cat.012")
								null -> S.t("cat.014")
								else -> S.t("cat.013")
							},
							style = MaterialTheme.typography.bodySmall,
							color = muted
						)
					}

					Spacer(Modifier.height(Space.lg))
					IknaRule()
					Spacer(Modifier.height(Space.lg))

					val decks = decksFor(list, filter)
					if (decks.isEmpty()) {
						Column {
							Text(
								text = S.t("cat.030"),
								style = MaterialTheme.typography.bodyMedium,
								color = muted
							)
							Spacer(Modifier.height(Space.lg))
							IknaLatticePlaceholder()
						}
					} else {
						decks.forEach { deck ->
							DeckRow(
								deck = deck,
								open = openId == deck.id,
								installed = installed.contains(catalogPackId(deck.id)),
								busy = busyId == deck.id,
								blocked = (busyId != null && busyId != deck.id) || previewLoading,
								importing = importing && busyId == deck.id,
								fraction = progressFractionOf(readBytes, totalBytes),
								percent = progressPercentOf(readBytes, totalBytes),
								previewLoading = previewLoading && previewId == deck.id,
								previewFailed = previewFailed && previewId == deck.id,
								previewCards = if (previewId == deck.id) previewCards else emptyList(),
								onOpen = {
									val next = if (openId == deck.id) null else deck.id
									openId = next
									if (next != previewId) {
										previewToken++
										previewId = null
										previewLoading = false
										previewFailed = false
										previewCards = emptyList()
									}
								},
								onPreview = { preview(deck) },
								onSource = { id -> openTatoeba(context, id) },
								onInstall = { install(deck) }
							)
							Spacer(Modifier.height(Space.md))
							IknaRule()
							Spacer(Modifier.height(Space.md))
						}
					}

					if (list.builtAt.isNotEmpty()) {
						Spacer(Modifier.height(Space.sm))
						Text(
							text = S.t("cat.026") + list.builtAt,
							style = MaterialTheme.typography.labelMedium,
							color = muted
						)
					}
				}

				val shown = note
				if (shown != null) {
					Spacer(Modifier.height(Space.lg))
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.background(MaterialTheme.colorScheme.surface)
							.padding(Space.md)
					) {
						Text(
							text = shown,
							style = MaterialTheme.typography.bodyMedium
						)
					}
					Spacer(Modifier.height(Space.md))
					IknaWideButton(
						label = S.t("add.026"),
						onClick = onBack,
						enabled = busyId == null
					)
				}

				Spacer(Modifier.height(Space.xxl))
			}
		}
		IknaBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
			IknaIconButton(glyph = IknaGlyph.BACK, onClick = onBack, label = S.t("a11y.001"))
		}
	}
}

/**
 * One deck, folded.
 *
 * The title and how much is in it on the row; the licence, who is credited and
 * where it came from under it, before there is anything to press. That order is
 * the point: a licence shown after a download is a licence nobody read.
 */
@Composable
private fun DeckRow(
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
						PreviewCard(card = card, number = index + 1, onSource = onSource)
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
private fun PreviewCard(
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

/** Language codes, four to a row, the way the deck languages are already asked. */
@Composable
private fun CodeChips(
	options: List<String>,
	current: String,
	onPick: (String) -> Unit
) {
	PickChips(
		options = options,
		label = { it.uppercase() },
		current = current,
		onPick = onPick
	)
}

/**
 * A row of answers with "any" first.
 *
 * Tapping the selected one clears it, so a filter can always be undone by
 * pressing the thing that set it -- there is no reset button anywhere in this app
 * and this screen is not the place to invent one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickChips(
	options: List<String>,
	label: (String) -> String,
	current: String,
	onPick: (String) -> Unit
) {
	val all = listOf("") + options
	// Four chips to a row was a guess about how wide a word is, and the guess
	// was wrong in Russian: «продвинутый» is wider than a quarter of the screen, so
	// the last chip in the row had its final letters cut off inside its own
	// border. Now the row wraps when the next chip does not fit, which makes
	// the layout a question for the text and the screen rather than for a
	// number typed here -- and it holds in all three languages of the app.
	FlowRow(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(Space.sm),
		verticalArrangement = Arrangement.spacedBy(Space.sm)
	) {
		all.forEach { option ->
			IknaChip(
				label = if (option.isEmpty()) S.t("cat.011") else label(option),
				selected = current == option,
				onClick = { onPick(if (current == option) "" else option) }
			)
		}
	}
}

private fun levelLabel(level: String): String = when (level) {
	"beginner" -> S.t("cat.027")
	"middle" -> S.t("cat.028")
	"advanced" -> S.t("cat.029")
	else -> level
}

/** The same list in a browser, for when the app could not fetch it. */
private fun openPage(context: Context) {
	runCatching {
		context.startActivity(
			Intent(Intent.ACTION_VIEW, Uri.parse(CATALOG_PAGE_URL))
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		)
	}
}

private fun openTatoeba(context: Context, id: String) {
	val url = tatoebaSentenceUrl(id) ?: return
	runCatching {
		context.startActivity(
			Intent(Intent.ACTION_VIEW, Uri.parse(url))
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		)
	}
}
