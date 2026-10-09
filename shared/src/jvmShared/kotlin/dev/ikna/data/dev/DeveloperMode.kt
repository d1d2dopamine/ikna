package dev.ikna.data.dev

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException

/** Which durable learner-data root the process opens at startup. */
enum class IknaDataProfile {
    REAL,
    DEVELOPER
}

/**
 * Developer-only policy overrides.
 *
 * Production policies still compute their normal verdict. An active developer
 * profile may continue despite product blockers, so testing cannot accidentally
 * teach the production policy to lie.
 */
data class DeveloperAccess(
    val active: Boolean = false
) {
    companion object {
        val NONE = DeveloperAccess()

        /** DEV may test ordinary product gates; REAL never receives an override. */
        fun forProfile(profile: IknaDataProfile, applyProductLimits: Boolean = false): DeveloperAccess =
            DeveloperAccess(active = profile == IknaDataProfile.DEVELOPER && !applyProductLimits)
    }
}

enum class DeveloperScenario(val id: String) {
    EMPTY("empty"),
    EARLY_HISTORY("early-history"),
    MATURE_HISTORY("mature-history"),
    BROWSE_READY("browse-ready"),
    RICH_STATISTICS("rich-statistics"),
    RETURN_AFTER_BREAK("return-after-break");

    companion object {
        fun fromId(value: String?): DeveloperScenario =
            entries.firstOrNull { it.id == value } ?: EMPTY
    }
}

/** Small bootstrap setting deliberately stored outside both real and developer preferences. */
class DataProfileStore(private val file: File) {
    fun current(): IknaDataProfile = runCatching {
        IknaDataProfile.valueOf(file.readText().trim())
    }.getOrDefault(IknaDataProfile.REAL)

    fun set(profile: IknaDataProfile) {
        writeBootstrap(file, profile.name)
    }
}

/** A confirmed scenario is applied at startup, before the interactive UI opens. */
class PendingDeveloperScenarioStore(private val file: File) {
    fun current(): DeveloperScenario? {
        if (!file.exists()) return null
        val id = file.readText().trim()
        return DeveloperScenario.entries.firstOrNull { it.id == id }
            ?: error("Invalid pending developer scenario")
    }
    fun set(scenario: DeveloperScenario) = writeBootstrap(file, scenario.id)
    fun clear() { Files.deleteIfExists(file.toPath()) }
}

private fun writeBootstrap(file: File, value: String) {
    file.absoluteFile.parentFile.mkdirs()
    val temp = Files.createTempFile(file.absoluteFile.parentFile.toPath(), file.name, ".tmp")
    try {
        Files.write(temp, value.toByteArray(Charsets.UTF_8))
        try {
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally { Files.deleteIfExists(temp) }
}

const val DEVELOPER_SCENARIO_REQUEST_FILE = "ikna-developer-scenario-request"

const val REAL_DATABASE_FILE = "ikna.db"
const val DEVELOPER_DATABASE_FILE = "ikna-developer.db"
const val DEVELOPER_SETTINGS_FILE = "ikna-developer-settings.preferences_pb"
const val DATA_PROFILE_FILE = "ikna-data-profile"

fun databaseFileName(profile: IknaDataProfile): String = when (profile) {
    IknaDataProfile.REAL -> REAL_DATABASE_FILE
    IknaDataProfile.DEVELOPER -> DEVELOPER_DATABASE_FILE
}
