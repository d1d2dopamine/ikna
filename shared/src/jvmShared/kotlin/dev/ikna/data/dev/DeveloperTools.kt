package dev.ikna.data.dev

import dev.ikna.data.prefs.SettingsStore
import dev.ikna.data.repo.LearningRepository
import dev.ikna.domain.session.BrowseAvailability

enum class DeveloperDestination { SESSION, BROWSE, STATS, CATALOG, SEARCH }
data class DeveloperDeck(val id: String, val title: String)
data class DeveloperDiagnostics(
    val capturedAt: Long,
    val planReason: String?, val governorReason: String,
    val allowedNew: Int, val capacity: Int, val pending: Int, val planSize: Int,
    val due: Int, val backlog: Int, val reviewRows: Int, val retiredCards: Int,
    val overrideEnabled: Boolean, val decks: List<DeveloperDeck>
)

/** All operations are backed by the same settings/repository as normal features. */
class DeveloperTools(
    private val profile: IknaDataProfile,
    private val learning: LearningRepository,
    private val settings: SettingsStore
) {
    init { require(profile == IknaDataProfile.DEVELOPER) }
    suspend fun snapshot(): DeveloperDiagnostics = learning.developerDiagnostics()
    suspend fun applyProductLimits(on: Boolean) = settings.setDeveloperApplyProductLimits(on)
    /** Explicit feature check: the normal Browse path may create today's plan/settle credits. */
    suspend fun checkBrowse(deckId: String): BrowseAvailability =
        requireNotNull(learning.browseDeckAvailability(listOf(deckId))[deckId])
}
