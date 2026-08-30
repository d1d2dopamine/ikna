pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Ikna"

// :shared holds everything that is not tied to a platform -- the scheduler, the
// database, the repositories, the settings, the six string tables and the theme.
// :app and :desktop are the two front doors onto it.
include(":shared")
include(":app")
include(":desktop")
