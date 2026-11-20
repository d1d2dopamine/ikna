package dev.ikna.ui.decks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.ikna.data.db.ChunkEntity
import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.prefs.suppressedOf
import dev.ikna.data.repo.DeckRepository
import dev.ikna.ui.text.S
import dev.ikna.ui.theme.IknaTextButton
import dev.ikna.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun IknaHiddenDeckCards(
    deckId: String,
    suppressed: String,
    decks: DeckRepository,
    settings: SettingsStore
) {
    if (suppressed.isBlank()) return
    var expanded by remember(deckId) { mutableStateOf(false) }
    var busy by remember(deckId) { mutableStateOf(false) }
    var failed by remember(deckId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cards by produceState(emptyList<ChunkEntity>(), deckId, suppressed, expanded) {
        value = emptyList()
        if (expanded) {
            failed = false
            try {
                value = decks.hiddenCards(deckId, suppressedOf(suppressed).toSet())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            }
        }
    }
    IknaTextButton(S.t("deck.hidden"), onClick = { expanded = !expanded })
    if (expanded) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (failed) Text(S.t("today.deck_change_failed"))
            for (card in cards) {
                Text(card.contextSentence, style = MaterialTheme.typography.bodyMedium)
                IknaTextButton(S.t("deck.restore_card"), enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            settings.restoreSuppressedChunk(card.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            busy = false
                        }
                    }
                })
            }
        }
    }
}
