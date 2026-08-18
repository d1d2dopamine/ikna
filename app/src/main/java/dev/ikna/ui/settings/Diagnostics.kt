package dev.ikna.ui.settings

import android.content.Context
import android.os.Build
import dev.ikna.AppContainer
import dev.ikna.data.db.IKNA_DATABASE_VERSION
import dev.ikna.data.repo.CURRENT_SCHEDULER_VERSION

/**
 * A support summary that never includes deck names, card text, review times,
 * paths, account data or device identifiers. It is built only after the person
 * opens the diagnostics block and remains on the phone until they press copy.
 */
internal data class DiagnosticsSnapshot(
    val versionName: String,
    val versionCode: Long,
    val androidRelease: String,
    val androidSdk: Int,
    val abi: String,
    val databaseVersion: Int,
    val schedulerVersion: Int,
    val interfaceLanguage: String,
    val deckCount: Int,
    val activeDeckCount: Int,
    val chunkCount: Int,
    val introducedCount: Int,
    val knownCount: Int
)

internal fun diagnosticsText(value: DiagnosticsSnapshot): String = buildString {
    appendLine("ikna diagnostics")
    appendLine("version=${value.versionName}")
    appendLine("versionCode=${value.versionCode}")
    appendLine("android=${value.androidRelease} (SDK ${value.androidSdk})")
    appendLine("abi=${value.abi}")
    appendLine("database=${value.databaseVersion}")
    appendLine("scheduler=FSRS-${value.schedulerVersion}")
    appendLine("interface=${value.interfaceLanguage}")
    appendLine("decks=${value.deckCount}")
    appendLine("activeDecks=${value.activeDeckCount}")
    appendLine("chunks=${value.chunkCount}")
    appendLine("introduced=${value.introducedCount}")
    append("known=${value.knownCount}")
}

internal suspend fun collectDiagnostics(
    context: Context,
    container: AppContainer
): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val settings = container.settings.current()
    val decks = container.deckRepository.decks()
    return diagnosticsText(
        DiagnosticsSnapshot(
            versionName = packageInfo.versionName.orEmpty(),
            versionCode = packageInfo.longVersionCode,
            androidRelease = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().ifBlank { "unknown" },
            databaseVersion = IKNA_DATABASE_VERSION,
            schedulerVersion = CURRENT_SCHEDULER_VERSION,
            interfaceLanguage = settings.language,
            deckCount = decks.size,
            activeDeckCount = decks.count { it.isActive },
            chunkCount = decks.sumOf { it.total },
            introducedCount = decks.sumOf { it.introduced },
            knownCount = decks.sumOf { it.known }
        )
    )
}
