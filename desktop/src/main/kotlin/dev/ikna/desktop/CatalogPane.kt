package dev.ikna.desktop
import dev.ikna.ui.catalog.IknaCatalogDeckRow
import dev.ikna.data.catalog.tatoebaSentenceUrl

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
    var lang by remember { mutableStateOf<String?>(null) }
    var meaning by remember { mutableStateOf<String?>(null) }
    var subject by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busyDeck by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var installed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var openDeckId by remember { mutableStateOf<String?>(null) }
    var previewToken by remember { mutableStateOf(0) }
    var previewFor by remember { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<List<CatalogPreviewCard>>(emptyList()) }

    suspend fun refreshInstalled() {
        installed = runCatching {
            container.deckRepository.decks().map { it.id }.toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun load() {
        loading = true
        index = runCatching { fetch.index() }.getOrNull()
        loading = false
    }

    LaunchedEffect(Unit) {
        refreshInstalled()
        load()
    }

    val current = index
    val decks = current?.decks.orEmpty()
        .filter { lang == null || it.lang == lang }
        .filter { meaning == null || it.meaningLang == meaning }
        .filter { subject == null || it.subject == subject }
        .filter { level == null || it.level == level }

    DesktopScrollablePane(S.t("cat.001"), onBack, titleStyle = MaterialTheme.typography.headlineSmall) {

Spacer(Modifier.height(Space.lg))
Spacer(Modifier.height(Space.xs))
Text(
    text = S.t("cat.002"),
    style = MaterialTheme.typography.bodySmall,
    color = palette.muted
)

Spacer(Modifier.height(Space.lg))

when {
    loading -> {
        Text(
            text = S.t("cat.003"),
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )
        Spacer(Modifier.height(Space.lg))
        IknaLatticePlaceholder()
    }

    current == null -> {
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

    else -> {
        Text(
            text = S.t("cat.026") + current.builtAt,
            style = MaterialTheme.typography.labelSmall,
            color = palette.muted
        )

        Spacer(Modifier.height(Space.lg))
        FilterRow(
            title = S.t("cat.007"),
            values = current.decks.map { it.lang }.distinct().sorted(),
            selected = lang,
            palette = palette,
            labelOf = { it.uppercase() },
            onPick = { picked -> lang = picked; meaning = null; subject = null; level = null }
        )
        FilterRow(
            title = S.t("cat.008"),
            values = current.decks
                .filter { lang == null || it.lang == lang }
                .map { it.meaningLang }.distinct().sorted(),
            selected = meaning,
            palette = palette,
            labelOf = { it.uppercase() },
            onPick = { picked -> meaning = picked }
        )
        FilterRow(
            title = S.t("cat.009"),
            values = current.decks
                .map { it.subject }.filter { it.isNotEmpty() }
                .distinct().sorted(),
            selected = subject,
            palette = palette,
            labelOf = { it },
            onPick = { picked -> subject = picked }
        )
        FilterRow(
            title = S.t("cat.010"),
            values = listOf("beginner", "middle", "advanced")
                .filter { value -> current.decks.any { it.level == value } },
            selected = level,
            palette = palette,
            labelOf = { levelLabel(it) },
            onPick = { picked -> level = picked }
        )

        // How well this pair is served, computed by the
        // pipeline rather than decided here.
        val pair = current.pairs.firstOrNull {
            it.lang == lang && it.meaningLang == meaning
        }
        if (lang != null && meaning != null) {
            Spacer(Modifier.height(Space.md))
            Text(
                text = when {
                    pair == null -> S.t("cat.014")
                    pair.tier == TIER_FULL -> S.t("cat.012")
                    else -> S.t("cat.013")
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted
            )
        }

        Spacer(Modifier.height(Space.lg))
        IknaRule()

        if (decks.isEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Text(
                text = S.t("cat.030"),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
            Spacer(Modifier.height(Space.lg))
            IknaLatticePlaceholder()
        }

        decks.forEach { deck ->
            val packId = packIdOf(deck)
            Spacer(Modifier.height(Space.lg))
            IknaCatalogDeckRow(deck = deck, open = openDeckId == packId,
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
                            note = null
                            val text = runCatching {
                                fetch.deck(deck) { read, total ->
                                    progress = if (total <= 0L) 0f
                                    else (read.toFloat() / total).coerceIn(0f, 1f)
                                }
                            }.getOrNull()
                            if (text == null) {
                                busyDeck = null
                                note = S.t("cat.020")
                                return@launch
                            }
                            progress = 1f
                            val report = runCatching {
                                container.packLoader.importJsonl(
                                    packId = packId,
                                    title = deck.title,
                                    lang = deck.lang,
                                    text = text
                                )
                            }.getOrNull()
                            busyDeck = null
                            if (report == null || report.installed == 0) {
                                note = S.t("cat.021")
                                return@launch
                            }
                            runCatching {
                                container.learningRepository.invalidatePlan()
                            }
                            refreshInstalled()
                            onChanged()
                            note = S.t("cat.022") + report.installed
                        }
                })
            Spacer(Modifier.height(Space.md)); IknaRule()
        }

        note?.let { text ->
            Spacer(Modifier.height(Space.lg))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink
            )
        }

        Spacer(Modifier.height(Space.lg))
        IknaTextButton(
            label = S.t("cat.005"),
            onClick = { openInBrowser(CATALOG_PAGE_URL) },
            color = palette.muted
        )
    }
}

Spacer(Modifier.height(Space.xxl))

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
