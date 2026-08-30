package dev.ikna.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.repo.DeckSummary
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import dev.ikna.ui.theme.IknaTheme
import dev.ikna.ui.theme.IknaWordmark
import dev.ikna.ui.theme.paletteFor
import dev.ikna.ui.theme.rememberContentFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Which screen the right-hand pane is showing. */
enum class Pane { SESSION, DECK, SETTINGS, STATS }

@Composable
fun IknaDesktopApp(container: DesktopContainer) {
    val settings by container.settings.flow.collectAsState(initial = IknaSettings())

    // A desktop window has no reliable light/dark signal to read, so the
    // lighting follows the chosen palette and defaults to dark, which is what
    // the app has always looked like.
    val palette = paletteFor(settings, systemDark = true)

    LaunchedEffect(settings.language) { S.apply(settings.language) }

    val contentFont = rememberContentFont(settings.fontName)

    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { container.install() }
        ready = true
    }

    IknaTheme(
        palette = palette,
        contentFont = contentFont,
        motionEnabled = settings.animations
    ) {
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(S.t("onb.007"), color = palette.muted, fontSize = 13.sp)
            }
        } else {
            DesktopShell(container, settings, palette)
        }
    }
}

/**
 * The two-pane layout.
 *
 * A phone shows one screen at a time because it has to. The pairing that earns
 * its place in a window is the deck list beside the cards: the list is how you
 * choose what to study and how you see what is left, and on the phone that costs
 * a screen change in both directions.
 */
@Composable
fun DesktopShell(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette
) {
    var pane by remember { mutableStateOf(Pane.SESSION) }
    var openDeck by remember { mutableStateOf<String?>(null) }
    var sessionDeck by remember { mutableStateOf<String?>(null) }
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var remaining by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var answered by remember { mutableStateOf(0) }
    var target by remember { mutableStateOf(0) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        decks = runCatching { container.deckRepository.decks() }.getOrDefault(emptyList())
        remaining = runCatching { container.learningRepository.remainingByDeck() }
            .getOrDefault(emptyMap())
        answered = runCatching { container.learningRepository.answeredToday() }.getOrDefault(0)
        target = runCatching { container.learningRepository.currentDailyTarget() }.getOrDefault(0)
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(330.dp).fillMaxHeight().padding(20.dp)) {
            if (settings.showWordmark) {
                IknaWordmark(height = 20.dp, ink = palette.ink, dot = palette.accent)
                Spacer(Modifier.height(18.dp))
            }

            Text(
                S.t("deck.007") + "  " + answered + " / " + target,
                color = palette.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(14.dp))

            PaneButton(S.t("pc.003"), pane == Pane.SESSION, palette) {
                sessionDeck = null
                pane = Pane.SESSION
            }
            Spacer(Modifier.height(10.dp))

            Text(S.t("deck.004"), color = palette.muted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f)) {
                if (decks.isEmpty()) {
                    Text(S.t("deck.005"), color = palette.muted, fontSize = 12.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(decks, key = { it.id }) { deck ->
                            DeckRow(
                                deck = deck,
                                due = remaining[deck.id] ?: 0,
                                selected = openDeck == deck.id && pane == Pane.DECK,
                                palette = palette,
                                onStudy = {
                                    sessionDeck = deck.id
                                    pane = Pane.SESSION
                                },
                                onOpen = {
                                    openDeck = deck.id
                                    pane = Pane.DECK
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
            Spacer(Modifier.height(10.dp))

            PaneButton(S.t("stats.001"), pane == Pane.STATS, palette) { pane = Pane.STATS }
            Spacer(Modifier.height(6.dp))
            PaneButton(S.t("set.012"), pane == Pane.SETTINGS, palette) { pane = Pane.SETTINGS }
        }

        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.line))

        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (pane) {
                Pane.SESSION -> SessionPane(
                    container = container,
                    settings = settings,
                    palette = palette,
                    deckId = sessionDeck,
                    onChanged = { reload += 1 }
                )

                Pane.DECK -> {
                    val id = openDeck
                    if (id == null) {
                        Centered(S.t("pc.004"), palette)
                    } else {
                        DeckPane(
                            container = container,
                            settings = settings,
                            palette = palette,
                            deckId = id,
                            onChanged = { reload += 1 },
                            onDeleted = {
                                openDeck = null
                                pane = Pane.SESSION
                                reload += 1
                            }
                        )
                    }
                }

                Pane.SETTINGS -> SettingsPane(container, settings, palette)
                Pane.STATS -> StatsPane(container, palette)
            }
        }
    }
}
