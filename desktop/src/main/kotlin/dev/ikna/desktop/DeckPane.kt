package dev.ikna.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ikna.data.prefs.IknaSettings
import dev.ikna.data.prefs.phoneticsFor
import dev.ikna.data.repo.DeckSummary
import dev.ikna.data.repo.NO_LANG
import dev.ikna.domain.phonetics.PhoneticsMode
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaPalette
import kotlinx.coroutines.launch

/** The languages the transcription pipeline covers, plus the subject-deck case. */
private val DECK_LANGS = listOf("en", "ru", "pl", "es", "fr", "de", "it", "pt")

/**
 * One deck's settings.
 *
 * This is where the transcription switch lives -- per deck, in the deck's own
 * settings, which is what was asked for: not at download time and not a global
 * preference, because a session draws from several decks at once and Polish and
 * Spanish can reasonably want different answers.
 */
@Composable
fun DeckPane(
    container: DesktopContainer,
    settings: IknaSettings,
    palette: IknaPalette,
    deckId: String,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var deck by remember(deckId) { mutableStateOf<DeckSummary?>(null) }
    var title by remember(deckId) { mutableStateOf("") }
    var confirmDelete by remember(deckId) { mutableStateOf(false) }
    var reload by remember(deckId) { mutableStateOf(0) }

    LaunchedEffect(deckId, reload) {
        val loaded = runCatching { container.deckRepository.deck(deckId) }.getOrNull()
        deck = loaded
        title = loaded?.title ?: ""
    }

    val current = deck
    if (current == null) {
        Centered(S.t("sess.001"), palette)
        return
    }

    val mode = settings.phoneticsFor(current.id)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(40.dp)
            .widthIn(max = 720.dp)
    ) {
        Text(current.title, color = palette.ink, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            current.known.toString() + " / " + current.total + "  " + S.t("deck.014"),
            color = palette.muted,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.013"), palette)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            IknaButton(S.t("sess.014"), palette) {
                scope.launch {
                    runCatching { container.deckRepository.rename(current.id, title) }
                    reload += 1
                    onChanged()
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.003"), palette)
        Spacer(Modifier.height(4.dp))
        Text(S.t("dp.004"), color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (code in DECK_LANGS) {
                IknaButton(code.uppercase(), palette, filled = current.lang == code) {
                    scope.launch {
                        runCatching { container.deckRepository.setLang(current.id, code) }
                        reload += 1
                        onChanged()
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        IknaButton(S.t("add.057"), palette, filled = current.lang == NO_LANG) {
            scope.launch {
                runCatching { container.deckRepository.setLang(current.id, NO_LANG) }
                reload += 1
                onChanged()
            }
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("dp.014"), palette)
        Spacer(Modifier.height(4.dp))
        Text(S.t("dp.015"), color = palette.muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IknaButton(S.t("dp.016"), palette, filled = mode == PhoneticsMode.RESPELL) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.RESPELL) }
            }
            IknaButton(S.t("dp.017"), palette, filled = mode == PhoneticsMode.IPA) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.IPA) }
            }
            IknaButton(S.t("dp.018"), palette, filled = mode == PhoneticsMode.OFF) {
                scope.launch { container.settings.setDeckPhonetic(current.id, PhoneticsMode.OFF) }
            }
        }
        if (!current.hasPhonetics) {
            Spacer(Modifier.height(8.dp))
            Text(S.t("dp.019"), color = palette.muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle(S.t("a11y.006"), palette)
        Spacer(Modifier.height(10.dp))
        IknaButton(
            if (current.isActive) S.t("dp.001") else S.t("dp.002"),
            palette,
            filled = current.isActive
        ) {
            scope.launch {
                runCatching { container.deckRepository.setActive(current.id, !current.isActive) }
                reload += 1
                onChanged()
            }
        }

        Spacer(Modifier.height(36.dp))

        IknaButton(if (confirmDelete) S.t("dp.008") else S.t("dp.007"), palette) {
            if (!confirmDelete) {
                confirmDelete = true
            } else {
                scope.launch {
                    runCatching { container.deckRepository.delete(current.id) }
                    onDeleted()
                }
            }
        }
        if (confirmDelete) {
            Spacer(Modifier.height(8.dp))
            Text(S.t("dp.009"), color = palette.muted, fontSize = 11.sp)
        }

        Spacer(Modifier.height(40.dp))
    }
}
