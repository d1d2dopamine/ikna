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

// ---------------------------------------------------------------------------
// The version is written here and nowhere else.
//
// It used to be derived from the CI run number, which was fine for as long as
// this repository was the only thing that ever built the app. It is not: anyone
// who clones it builds on a machine with no run number and no tag, where every
// build would come out as version 1 and would refuse to install over a real one.
//
// So both numbers are bumped by hand when releasing, and the tag carries the
// same number. The release workflow refuses to publish when the tag and
// appVersionName disagree.
//
// appVersionCode is major * 100000 + minor * 10000 + patch * 100. It only ever goes up,
// which is what Android requires to install an update over an older build. It
// also sits far above every CI build number this project ever produced, so a
// release always installs over a CI build and never the other way round.
//
// Epochs. The version name carries a word after the number: the epoch the
// build belongs to. proof is the one where the app is feature-complete and
// what is left is testing, small corrections and polish; press is the next
// one, and it starts when proof has nothing left to correct. Both words are
// spelled in capitals, and they are part of the version people read, not a
// build flavour: there is one artifact and one number, as before.
//
// The numbering restarted at 0.1.0 with proof, because the pre-epoch 0.x line
// counted something else and carrying 0.6.1 forward would have implied the two
// scales are comparable. appVersionCode does NOT restart with it. Android
// refuses to install an APK whose code is lower than the installed one, and the
// only irreplaceable thing in this app is the review log inside that install --
// so the counter keeps climbing across the reset and the formula above applies
// per epoch, offset past everything the pre-epoch line ever shipped.
// ---------------------------------------------------------------------------
val appVersionName = "0.2.0 proof"
val appVersionCode = 100020000        // proof epoch: 100000000 + 0.2.0

// A build from a clone has to be able to come out unsigned: the key committed
// here is this project's, and nobody else should be shipping APKs under it.
// Passing -Pikna.unsigned=true switches the signing config off without anyone
// having to patch this file during their build.
val unsignedBuild =
    (project.findProperty("ikna.unsigned") as String?)?.toBoolean() == true
val signWithFixedKey = hasFixedKey && !unsignedBuild

android {
    namespace = "dev.ikna"
    compileSdk = 35

    defaultConfig {
        // Never change this, and never add a debug suffix: a different
        // applicationId is a different app with a different database.
        applicationId = "dev.ikna"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        create("fixed") {
            if (signWithFixedKey) {
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
            if (signWithFixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signWithFixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    // -----------------------------------------------------------------------
    // Two downloads, one app.
    //
    // lite is the app as it has always been: the phone's own speech engine,
    // every language the user already has installed, and an APK measured in
    // single megabytes. It is the default and it is what CI builds.
    //
    // voice is the same app with a neural synthesiser and its model packed
    // inside, for phones whose own engine has nothing worth listening to. Same
    // applicationId, same database, same review history: one installs over the
    // other and the user loses nothing by switching.
    //
    // The fork is two source sets holding one file each -- app/src/lite and
    // app/src/voice -- plus this block. No code outside them knows which build
    // it is in, and the lite build cannot accidentally pull the runtime in:
    // the dependency is flavour-scoped, so it does not exist for lite at all.
    // -----------------------------------------------------------------------
    flavorDimensions += "speech"

    productFlavors {
        create("lite") {
            dimension = "speech"
        }
        create("voice") {
            dimension = "speech"
            // So a screenshot or a bug report says which of the two is running.
            versionNameSuffix = " voice"
        }
    }

    buildFeatures { compose = true }

    // An .onnx model is already compressed. Deflating it again costs build time,
    // saves nothing, and forces the packager's copy to be inflated in full
    // before the runtime can read a byte of it.
    androidResources {
        noCompress += listOf("onnx", "bin")
    }

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

    // Walking the folder somebody picked in the file browser. A picked tree is a
    // pile of content:// documents rather than a path, and this is the supported
    // way to read one. Tens of kilobytes.
    implementation("androidx.documentfile:documentfile:1.0.1")
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

    // The neural speech runtime -- voice build only.
    //
    // A local .aar, not a coordinate: it carries native libraries for four
    // architectures, it is tens of megabytes, and it is not committed.
    // tools/voice/fetch-voice.sh puts it in app/libs together with the model,
    // and the release workflow runs that script before building the voice APK.
    //
    // Being flavour-scoped is what keeps a fresh clone buildable: the lite
    // build never looks in app/libs, so an empty or missing directory is not an
    // error for anyone who only wants the normal app.
    "voiceImplementation"(
        fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar")))
    )
}
