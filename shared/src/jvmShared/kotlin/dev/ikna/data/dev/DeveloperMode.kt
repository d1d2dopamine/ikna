package dev.ikna.data.dev

import java.io.File

/** Which durable learner-data root the process opens at startup. */
enum class IknaDataProfile {
    REAL,
    DEVELOPER
}

/**
 * Developer-only policy overrides.
 *
 * Production policies still compute their normal verdict. This flag only lets the
 * sandbox choose to continue despite that verdict, so developer testing cannot
 * accidentally teach the production policy to lie.
 */
data class DeveloperAccess(
    val active: Boolean = false,
    val ignoreRestrictions: Boolean = false
) {
    companion object {
        val NONE = DeveloperAccess()
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
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(profile.name)
        if (!temp.renameTo(file)) {
            file.writeText(profile.name)
            temp.delete()
        }
    }
}

const val REAL_DATABASE_FILE = "ikna.db"
const val DEVELOPER_DATABASE_FILE = "ikna-developer.db"
const val DEVELOPER_SETTINGS_FILE = "ikna-developer-settings.preferences_pb"
const val DATA_PROFILE_FILE = "ikna-data-profile"

fun databaseFileName(profile: IknaDataProfile): String = when (profile) {
    IknaDataProfile.REAL -> REAL_DATABASE_FILE
    IknaDataProfile.DEVELOPER -> DEVELOPER_DATABASE_FILE
}
