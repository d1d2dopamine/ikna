package dev.ikna.desktop
import dev.ikna.ui.catalog.IknaCatalogDeckRow
import dev.ikna.data.catalog.CatalogFilter
import dev.ikna.data.catalog.decksFor
import dev.ikna.data.catalog.learnableLangs
import dev.ikna.data.catalog.levelsFor
import dev.ikna.data.catalog.meaningLangsFor
import dev.ikna.data.catalog.normalizeCatalogFilter
import dev.ikna.data.catalog.subjectsFor
import dev.ikna.data.catalog.tatoebaSentenceUrl
import dev.ikna.data.catalog.tierOf

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ikna.data.catalog.CATALOG_PAGE_URL
import dev.ikna.data.catalog.CatalogDeck
import dev.ikna.data.catalog.CatalogFetch
import dev.ikna.data.catalog.CatalogIndex
import dev.ikna.data.catalog.CatalogPreviewCard
import dev.ikna.data.catalog.TIER_FULL
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaChip
import dev.ikna.ui.theme.IknaLatticePlaceholder
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaRule
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.IknaWideButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI

/*
 * The catalogue -- the phone's screen on a window.
 *
 * Same list, same filters, same order of questions: what is being learned, in
 * which language the meanings should be, topic, level. Every licence and every
 * credit is shown before anything is downloaded, three real cards can be read
 * before the file is asked for, and a deck already installed says so instead of
 * offering itself again.
 */

private const val INSTALLED_VERSION = "0.10.0"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogPane(
    container: DesktopContainer,
    palette: IknaPalette,
    onChanged: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val fetch = remember { CatalogFetch(installedVersion = INSTALLED_VERSION) }

    var index by remember { mutableStateOf<CatalogIndex?>(null) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(CatalogFilter()) }
    val notes = remember { mutableStateMapOf<String, String>() }
    val planRefreshFailures = remember { mutableStateMapOf<String, Boolean>() }
    var busyDeck by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var openDeckId by remember { mutableStateOf<String?>(null) }
    var previewToken by remember { mutableStateOf(0) }
    var previewFor by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<CatalogPreviewCard>>(emptyList()) }

    suspend fun refreshInstalled() {
        installed = try {
            container.deckRepository.decks().map { it.id }.toSet()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            installed
        }
    }

    suspend fun refreshStudyPlan(packId: String) {
        planRefreshFailures[packId] = !dev.ikna.data.catalog.refreshCatalogStudyPlan {
            container.learningRepository.invalidatePlan()
        }
    }

    suspend fun load() {
        loading = true
        index = runCatching { fetch.index() }.getOrNull()
        index?.let { filter = normalizeCatalogFilter(it, filter) }
        loading = false
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
        load()
    }

    val current = index
    val decks = current?.let { decksFor(it, filter) }.orEmpty()

    // The pane owns jobs and status; lazy row disposal only releases the presentation.
    DesktopLazyPane(S.t("cat.001"), onBack) {
        item(key = "catalog-intro") {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(Space.lg))
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = S.t("cat.002"),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted
                )

                Spacer(Modifier.height(Space.lg))
            }
        }
        when {
            loading -> {
                item(key = "catalog-loading") {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = S.t("cat.003"),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted
                        )
                        Spacer(Modifier.height(Space.lg))
                        IknaLatticePlaceholder()
                    }
                }
            }
            current == null -> {
                item(key = "catalog-error") {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = S.t("cat.004"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.ink
                        )
                        Spacer(Modifier.height(Space.md))
                        IknaWideButton(
                            label = S.t("cat.006"),
                            filled = true,
                            onClick = { scope.launch { load() } }
                        )
                        Spacer(Modifier.height(Space.md))
                        IknaTextButton(
                            label = S.t("cat.005"),
                            onClick = { openInBrowser(CATALOG_PAGE_URL) },
                            color = palette.accent
                        )
                    }
                }
            }
            else -> {
                item(key = "catalog-filters") {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = S.t("cat.026") + current.builtAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted
                        )

                        Spacer(Modifier.height(Space.lg))
                        FilterRow(
                            title = S.t("cat.007"),
                            values = learnableLangs(current),
                            selected = filter.lang.ifEmpty { null },
                            palette = palette,
                            labelOf = { it.uppercase() },
                            onPick = { picked -> filter = normalizeCatalogFilter(current, filter.copy(lang = picked.orEmpty())) }
                        )
                        FilterRow(
                            title = S.t("cat.008"),
                            values = meaningLangsFor(current, filter.lang),
                            selected = filter.meaningLang.ifEmpty { null },
                            palette = palette,
                            labelOf = { it.uppercase() },
                            onPick = { picked -> filter = normalizeCatalogFilter(current, filter.copy(meaningLang = picked.orEmpty())) }
                        )
                        FilterRow(
                            title = S.t("cat.009"),
                            values = subjectsFor(current, filter),
                            selected = filter.subject.ifEmpty { null },
                            palette = palette,
                            labelOf = { it },
                            onPick = { picked -> filter = normalizeCatalogFilter(current, filter.copy(subject = picked.orEmpty())) }
                        )
                        FilterRow(
                            title = S.t("cat.010"),
                            values = levelsFor(current, filter),
                            selected = filter.level.ifEmpty { null },
                            palette = palette,
                            labelOf = { levelLabel(it) },
                            onPick = { picked -> filter = normalizeCatalogFilter(current, filter.copy(level = picked.orEmpty())) }
                        )

                        // How well this pair is served, computed by the
                        // pipeline rather than decided here.
                        val tier = tierOf(current, filter.lang, filter.meaningLang)
                        if (filter.lang.isNotEmpty() && filter.meaningLang.isNotEmpty()) {
                            Spacer(Modifier.height(Space.md))
                            Text(
                                text = when {
                                    tier == null -> S.t("cat.014")
                                    tier == TIER_FULL -> S.t("cat.012")
                                    else -> S.t("cat.013")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.muted
                            )
                        }

                        Spacer(Modifier.height(Space.lg))
                        IknaRule()
                    }
                }
                if (decks.isEmpty()) {
                    item(key = "catalog-empty") {
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(Space.lg))
                            Text(
                                text = S.t("cat.030"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted
                            )
                            Spacer(Modifier.height(Space.lg))
                            IknaLatticePlaceholder()
                        }
                    }
                }
                items(decks, key = { "deck:${it.id}" }, contentType = { "deck" }) { deck ->
                    Column(Modifier.fillMaxWidth()) {
                        val packId = packIdOf(deck)
                        Spacer(Modifier.height(Space.lg))
                        IknaCatalogDeckRow(deck = deck, resultNote = notes[packId],
                            planRefreshFailed = planRefreshFailures[packId] == true,
                            onRetryPlan = { scope.launch { refreshStudyPlan(packId) } }, open = openDeckId == packId,
                            installed = packId in installed, busy = busyDeck == packId,
                            blocked = busyDeck != null, importing = busyDeck == packId && progress >= 1f,
                            fraction = progress, percent = (progress * 100).toInt(),
                            previewLoading = previewLoading && previewFor == packId,
                            previewFailed = !previewLoading && previewFor == packId && preview.isEmpty(),
                            previewCards = if (previewFor == packId) preview else emptyList(),
                            onOpen = { openDeckId = if (openDeckId == packId) null else packId },
                            onSource = { id -> tatoebaSentenceUrl(id)?.let(::openInBrowser) },
                            onPreview = {
                                val request = ++previewToken
                                previewFor = packId; previewLoading = true; preview = emptyList()
                                scope.launch {
                                    val result = runCatching { fetch.preview(deck) }.getOrNull().orEmpty()
                                    if (request == previewToken) { preview = result; previewLoading = false }
                                }
                            }, onInstall = {
                                    scope.launch {
                                        busyDeck = packId
                                        progress = 0f
                                        notes.remove(packId)
                                        try {
                                            val text = fetch.deck(deck) { read, total ->
                                                progress = if (total <= 0L) 0f
                                                else (read.toFloat() / total).coerceIn(0f, 1f)
                                            }
                                            currentCoroutineContext().ensureActive()
                                            if (text == null) {
                                                notes[packId] = S.t("cat.020")
                                                return@launch
                                            }
                                            progress = 1f
                                            val report = try {
                                                container.importCatalogDeck(
                                                    packId = packId,
                                                    title = deck.title,
                                                    lang = deck.lang,
                                                    text = text
                                                )
                                            } catch (cancelled: CancellationException) {
                                                throw cancelled
                                            } catch (_: Exception) {
                                                null
                                            }
                                            currentCoroutineContext().ensureActive()
                                            if (report == null || report.installed == 0) {
                                                notes[packId] = S.t("cat.021")
                                                return@launch
                                            }
                                            installed = installed + packId
                                            refreshStudyPlan(packId)
                                            refreshInstalled()
                                            currentCoroutineContext().ensureActive()
                                            onChanged()
                                            notes[packId] = S.t("cat.022") + report.installed
                                        } finally {
                                            busyDeck = null
                                        }
                                    }
                            })
                        Spacer(Modifier.height(Space.md)); IknaRule()
                    }
                }
                item(key = "catalog-footer") {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(Space.lg))
                        IknaTextButton(
                            label = S.t("cat.005"),
                            onClick = { openInBrowser(CATALOG_PAGE_URL) },
                            color = palette.muted
                        )
                    }
                }
            }
        }
        item(key = "catalog-bottom-space") { Spacer(Modifier.height(Space.xxl)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    title: String,
    values: List<String>,
    selected: String?,
    palette: IknaPalette,
    labelOf: (String) -> String,
    onPick: (String?) -> Unit
) {
    if (values.isEmpty()) return
    Spacer(Modifier.height(Space.md))
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = palette.muted
    )
    Spacer(Modifier.height(Space.sm))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        IknaChip(
            label = S.t("cat.011"),
            selected = selected == null,
            onClick = { onPick(null) }
        )
        values.forEach { value ->
            IknaChip(
                label = labelOf(value),
                selected = selected == value,
                onClick = { onPick(if (selected == value) null else value) }
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

/** A stable local id for a catalogue deck, so a second visit knows it is here. */
private fun packIdOf(deck: CatalogDeck): String = "catalog-" + deck.id

private fun megabytes(bytes: Long): String {
    if (bytes <= 0L) return "0"
    val value = bytes.toDouble() / (1024.0 * 1024.0)
    return if (value >= 10) value.toInt().toString()
    else ((value * 10).toInt() / 10.0).toString()
}

internal fun openInBrowser(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }.onFailure { error -> logLine("browse failed: " + error) }
}
