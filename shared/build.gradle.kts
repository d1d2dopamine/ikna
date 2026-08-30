import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// ---------------------------------------------------------------------------
// The half of the application that does not know what a phone is.
//
// Plugin versions are written out in full and are identical to the ones in
// app/build.gradle.kts and desktop/build.gradle.kts. Two sibling projects may
// request the same plugin at the same version; requesting two different
// versions is what fails, so these three files move together or not at all.
//
// Compose Multiplatform 1.8.2 rather than 1.9.x: the 1.9 line is built on
// Compose 1.9 and Material3 1.4, which want compileSdk 36, and compileSdk 36
// needs a newer Android Gradle plugin than the 8.6.1 this project is pinned to.
// 1.8.2 is the newest release that compiles against compileSdk 35.
// ---------------------------------------------------------------------------
plugins {
    id("com.android.library") version "8.6.1"
    id("org.jetbrains.kotlin.multiplatform") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("org.jetbrains.compose") version "1.8.2"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

kotlin {
    jvmToolchain(17)

    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        // Both targets are the JVM, so almost all of the code lives in one
        // source set between commonMain and the two platforms. It is wired by
        // hand rather than with applyDefaultHierarchyTemplate() because the
        // template has no name for "Android and the desktop but not iOS", and
        // an intermediate source set it does not know about is silently left
        // out of every compilation.
        val commonMain by getting
        val jvmShared by creating { dependsOn(commonMain) }
        val androidMain by getting { dependsOn(jvmShared) }
        val desktopMain by getting { dependsOn(jvmShared) }

        // api, not implementation: :app compiles against Compose, Room and the
        // coroutines types that appear in this module's public signatures, and
        // it no longer declares any of them itself. One version of Compose in
        // the build, chosen here.
        jvmShared.dependencies {
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)

            // The shared-axis route change and the card's arrival animation
            // live in this module now, so the animation artifact is declared
            // here rather than being inherited from whatever :app happens to
            // pull in.
            api(compose.animation)

            // Room 2.7.2, deliberately neither 2.8 nor 3.0.
            //
            // 2.7 is the first stable Room that can generate a database for a
            // target that is not Android, which is the whole reason this module
            // exists. 2.8 is built against compileSdk 36, which AGP 8.6.1
            // cannot compile against; 3.0 removes the compatibility surface
            // this project has already stopped using but changes more besides.
            api("androidx.room:room-runtime:2.7.2")
            api("androidx.sqlite:sqlite:2.5.2")

            // The -core artifact, not -preferences: the Android one carries the
            // Context delegate this code no longer uses, and only the core
            // module builds for the desktop.
            api("androidx.datastore:datastore-preferences-core:1.1.1")

            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }

        androidMain.dependencies {
            api("androidx.sqlite:sqlite-framework:2.5.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
        }

        desktopMain.dependencies {
            // SQLite compiled into the jar. Windows has none to borrow, and this
            // build enables FTS4, which the deck search needs.
            implementation("androidx.sqlite:sqlite-bundled:2.5.2")
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
        }
    }
}

// Room's compiler runs once per target. add("kspAndroid", ...) rather than
// ksp(...): in a multiplatform project there is no single ksp configuration,
// and a dependency added to the wrong one is silently not applied -- the build
// then fails much later, complaining that the generated database class is
// missing.
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.7.2")
    add("kspDesktop", "androidx.room:room-compiler:2.7.2")
}

ksp {
    // Still app/schemas. The schema history is committed, the workflow uploads
    // it as an artifact and then checks that git is clean; moving it would mean
    // moving all three of those and rewriting SchemaTest, for nothing. Both
    // targets generate the same file from the same entities, so they agree.
    arg("room.schemaLocation", "${rootProject.projectDir}/app/schemas")

    // Room 2.7 generates Kotlin DAO implementations rather than Java, and does
    // so by default. It is written down instead of left implicit because it
    // changes what a query returning no rows does: the generated Kotlin throws
    // where the generated Java returned null. Every single-row query in Daos.kt
    // already declares a nullable return -- lastAnswer, forDay, latest -- so
    // there is nothing here for it to break, and if a later one forgets, the
    // build fails instead of the phone.
    arg("room.generateKotlin", "true")
}

android {
    namespace = "dev.ikna.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Keep the Android resources beside the Android Kotlin rather than in a
    // src/main folder that has nothing else in it.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
}
