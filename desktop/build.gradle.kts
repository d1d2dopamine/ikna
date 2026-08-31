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
    // Anki writes its newest collections compressed. The phone reads them
    // through the same library; the window needs its own copy because
    // :desktop does not depend on :app.
    implementation("com.github.luben:zstd-jni:1.5.6-8")
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

        // createReleaseDistributable does not just package the jars: it shrinks
        // them with ProGuard first, and ProGuard stops on any warning it was
        // not told to expect. The rules it needs, and the reason for each one,
        // are in compose-desktop.pro next to this file. The Compose plugin's
        // own defaults still apply -- this is added to them, not a replacement.
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

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

            // jlink builds the bundled runtime from the modules jdeps can see
            // in the bytecode, and it cannot see a reflective one. DataStore
            // serialises through a bundled protobuf-lite that reaches for
            // sun.misc.Unsafe, which lives in jdk.unsupported: without it the
            // first setting the user changes -- the palette, as it happened --
            // dies with NoClassDefFoundError and a blank "Error" box.
            //
            // The rest are the same kind of invisible dependency:
            //   jdk.crypto.ec   TLS to api.github.com for the update check
            //   jdk.localedata  dates and numbers outside the root locale
            //   java.sql        JDBC types Room's generated code touches
            //   java.naming     pulled in by java.sql
            //   java.management runtime hooks used by the coroutines machinery
            //   jdk.charsets    non-UTF-8 files on import
            modules(
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.localedata",
                "java.sql",
                "java.naming",
                "java.management",
                "jdk.charsets"
            )

            windows {
                menuGroup = "Ikna"
                shortcut = true
                dirChooser = true

                // Without this jpackage stamps the Compose Multiplatform
                // default onto the executable, the shortcut and the installer,
                // which is why the first build shipped with a Kotlin logo.
                // icon.ico is generated from the same wordmark the phone
                // launcher icon uses, by tools/make-desktop-icon.py. It has no
                // source of its own on purpose: the two marks cannot drift.
                iconFile.set(project.file("icon.ico"))
                // Fixed for the lifetime of the product: it is how Windows
                // recognises an installer as an upgrade of what is already
                // there rather than a second copy of it.
                upgradeUuid = "6f3c9c1e-4f2a-4b8d-9a1e-2d7b5c8e3a04"
            }
        }
    }
}
