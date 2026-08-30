import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// ---------------------------------------------------------------------------
// The Windows application.
//
// A plain Kotlin/JVM module rather than a second multiplatform one: it builds
// for exactly one thing, and :shared already publishes a jvm variant for it to
// resolve. Everything it draws comes from :shared, so the two applications
// cannot drift apart in the scheduler, the database or the wording.
// ---------------------------------------------------------------------------
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("androidx.sqlite:sqlite-bundled:2.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

// The decks and governor.json are read from app/src/main/assets rather than
// copied into this module. One catalogue, one starter deck, one set of tuning
// constants -- a second copy in the repository is a second copy to forget to
// update. ClasspathAssets looks for them under /assets, which is what `into`
// below produces.
tasks.named<Copy>("processResources") {
    from(rootProject.file("app/src/main/assets")) {
        into("assets")
    }
}

compose.desktop {
    application {
        mainClass = "dev.ikna.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Ikna"

            // jpackage refuses anything that is not MAJOR.MINOR.PATCH, so this
            // cannot simply be appVersionName from app/build.gradle.kts, which
            // is "0.10.0 press". Keep the numbers in step by hand when the
            // Android version changes.
            packageVersion = "0.10.0"

            description = "Ikna"
            vendor = "Ikna"

            windows {
                menuGroup = "Ikna"
                shortcut = true
                dirChooser = true
                // Fixed for the lifetime of the product: it is how Windows
                // recognises an installer as an upgrade of what is already
                // there rather than a second copy of it.
                upgradeUuid = "6f3c9c1e-4f2a-4b8d-9a1e-2d7b5c8e3a04"
            }
        }
    }
}
