// ---------------------------------------------------------------------------
// Build-wide plugin versions.
//
// Kotlin's Gradle plugin must be loaded once for the whole build. Declaring the
// same Kotlin version independently in :app, :shared and :desktop creates
// separate plugin classloaders; Gradle warns that this is unsupported and can
// break cross-project builds. Keep the versions here and let subprojects apply
// the plugins without repeating versions.
//
// This keeps the repository self-contained without a version catalog: there is
// still one ordinary file to review, and no generated `libs` accessor is
// required for the build scripts to compile.
// ---------------------------------------------------------------------------
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false

    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false

    id("org.jetbrains.compose") version "1.8.2" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
}
