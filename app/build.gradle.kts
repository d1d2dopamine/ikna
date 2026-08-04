plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
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

android {
    namespace = "dev.ikna"
    compileSdk = 35

    defaultConfig {
        // Never change this, and never add a debug suffix: a different
        // applicationId is a different app with a different database.
        applicationId = "dev.ikna"
        minSdk = 29
        targetSdk = 35
        versionCode = (System.getenv("RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1." + (System.getenv("RUN_NUMBER") ?: "1")

        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
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

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.lottie.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
