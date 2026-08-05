// ---------------------------------------------------------------------------
// Root build file: deliberately empty.
//
// It used to declare every plugin through the `libs` version catalog
// (gradle/libs.versions.toml). A version catalog is a file like any other, and
// that one is the single file in this repository that nothing else references,
// so it is also the easiest one to forget when pushing. When it is missing, the
// `libs` accessor simply does not exist and every build script that mentions it
// fails to compile with "Unresolved reference: libs" — before a single line of
// app code is even looked at.
//
// Plugin ids and versions now live directly in app/build.gradle.kts, the only
// module in this build. One module does not need a catalog; it needs a build
// that cannot be broken by a file that failed to make it into a commit.
// ---------------------------------------------------------------------------
