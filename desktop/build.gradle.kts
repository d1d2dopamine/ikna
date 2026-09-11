
// ---------------------------------------------------------------------------
// The desktop application: Windows, and now Linux.
//
// A plain Kotlin/JVM module rather than a second multiplatform one: it builds
// for exactly one thing, and :shared already publishes a jvm variant for it to
// resolve. Everything it draws comes from :shared, so the two applications
// cannot drift apart in the scheduler, the database or the wording.
//
// Linux added no source set, no second main() and no screen of its own. The
// JVM is the JVM: createReleaseDistributable asks jpackage for an application
// image for whatever machine it is running on, so the Linux half of this file
// is one block of packaging metadata. The single file a person downloads --
// the .AppImage -- is assembled from that application image afterwards by
// tools/appimage/build-appimage.sh, because no jpackage format is an AppImage.
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
    testImplementation("junit:junit:4.13.2")
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

            // Linux uses this same application image as the input to
            // tools/appimage/build-appimage.sh. NSIS is host-specific and never
            // participates in the Linux build.
            linux {
                // jpackage refuses an .ico here, and it copies whatever this
                // names into the application image as lib/Ikna.png -- which
                // is the first place build-appimage.sh looks for the icon.
                // The same 512px mark the running window loads from
                // resources, so the file on a Fedora dock, the window and the
                // phone launcher cannot drift apart.
                iconFile.set(project.file("src/main/resources/icon.png"))

                // What a desktop menu files it under, for the day somebody
                // integrates the AppImage into one.
                appCategory = "Education"
            }

            windows {
                // jpackage stamps the launcher in the application image. The
                // checked-in NSIS recipe owns setup, upgrades and shortcuts.
                // icon.ico comes from the same wordmark as the phone launcher.
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}

// The experiment is tested on a plain JVM as well as in Android's unit target.
kotlin.sourceSets.named("test") {
    kotlin.srcDir(rootProject.file("app/src/test/java/dev/ikna/domain/grading"))
    kotlin.srcDir(rootProject.file("app/src/test/java/dev/ikna/domain/fsrs"))
    kotlin.srcDir(rootProject.file("app/src/test/java/dev/ikna/domain/optimizer"))
}

tasks.register<JavaExec>("gradingLab") {
    dependsOn("classes")
    group = "verification"
    description = "Evaluate an explicit local JSONL log; never connects to a server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.ikna.desktop.grading.GradingLabKt")
    workingDir = rootProject.projectDir
    val input = providers.gradleProperty("grading.input")
    val output = providers.gradleProperty("grading.output")
        .orElse("build/grading/report.txt")
    // An explicit path is mandatory; real data is never searched for implicitly.
    doFirst {
        require(input.isPresent) { "Pass -Pgrading.input=path/to/log.jsonl" }
        args(input.get(), output.get())
    }
}
