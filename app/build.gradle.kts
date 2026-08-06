// ---------------------------------------------------------------------------
// Plugin ids and versions are written out in full, on purpose.
//
// They used to be `alias(libs.plugins.…)` entries resolved from
// gradle/libs.versions.toml. That catalog is generated into an accessor called
// `libs` only if the file is actually present in the checkout; when it is not,
// the build script itself fails to compile with "Unresolved reference: libs"
// and nothing else in the project ever gets a chance to run. A single module
// does not need the indirection, and this way the build depends on this file
// alone.
// ---------------------------------------------------------------------------
plugins {
    id("com.android.application") version "8.6.1"
    id("org.jetbrains.kotlin.android") version "2.0.20"
    // Compose compiler is a separate Gradle plugin since Kotlin 2.0; without it
    // AGP fails configuration as soon as buildFeatures.compose is enabled.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    id("com.google.devtools.ksp") version "2.0.20-1.0.25"
}

// ---------------------------------------------------------------------------
// Fixed signing identity.
//
// Gradle would otherwise create a fresh ~/.android/debug.keystore on every CI
// runner, producing a different signature per build. Android then refuses to
// install the new APK over the old one and you would have to uninstall first,
// which destroys the review history.
//
// So the keystore lives in the repository (ikna.keystore) with the password
// below. Nothing to generate, nothing to paste into repository secrets: clone,
// push, build, install updates forever.
//
// This is deliberately not a secret. The trade-off is written down in
// docs/KEYSTORE.md: the key only protects app-update identity for a personal
// app that is never published to a store. If this project is ever put on Google
// Play, replace this keystore with a private one before the first upload.
// ---------------------------------------------------------------------------
val keystoreFile = rootProject.file("ikna.keystore")
val keystorePassword = "iknafixedkey"
val keystoreAlias = "ikna"
val hasFixedKey = keystoreFile.exists()

// Build number from CI. `toIntOrNull` rather than `toInt`: an empty or unset
// RUN_NUMBER must produce version 1, not a configuration-time crash.
val runNumber = System.getenv("RUN_NUMBER")?.trim()?.toIntOrNull() ?: 1

android {
    namespace = "dev.ikna"
    compileSdk = 35

    defaultConfig {
        // Never change this, and never add a debug suffix: a different
        // applicationId is a different app with a different database.
        applicationId = "dev.ikna"
        minSdk = 29
        targetSdk = 35
        versionCode = runNumber
        versionName = "0.2." + runNumber
    }

    signingConfigs {
        create("fixed") {
            if (hasFixedKey) {
                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (hasFixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasFixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests {
            // The unit tests below are pure arithmetic, but Room and DataStore
            // types are on their classpath; stubbed Android calls return
            // defaults instead of throwing.
            isReturnDefaultValues = true
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Room writes its schema history here. Top level, not inside defaultConfig:
// this configures KSP, not a product flavour, and nesting it only worked by
// accident of Kotlin scoping.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
}
