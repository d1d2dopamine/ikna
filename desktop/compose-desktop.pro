# ---------------------------------------------------------------------------
# ProGuard rules for the Windows release build.
#
# createReleaseDistributable does not simply package the jars: Compose
# Multiplatform runs ProGuard over them first, and ProGuard stops the build on
# any warning it was not told to expect. This file is that list, plus the keep
# rules for the three things ProGuard cannot see being used.
#
# The Android half of this project has the same file, app/proguard-rules.pro,
# written for R8. The two are separate on purpose -- they shrink different jars
# with different tools -- but the reasoning is the same, and where a rule is
# here for the same reason it is there, the note says so.
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Shrink, but do not optimise.
#
# ProGuard's optimiser rewrites method signatures when it can prove a narrower
# type -- and on okio it proves it wrong. The 0.10.0 build that came out of
# here started with a dialog reading:
#
#   Bad return type
#   Location: okio/Okio__JvmOkioKt.source$33603375(Ljava/io/File;)Lokio/InputStreamSource;
#   Reason: Type 'okio/Source' is not assignable to 'okio/InputStreamSource'
#
# The $number suffix is the optimiser's own naming for a specialised method. It
# narrowed the return type to InputStreamSource, left the body returning Source,
# and the JVM verifier rejected the class the first time anything touched it.
# The build was green: bytecode this broken is only caught at startup.
#
# Shrinking is what makes the distribution small and it stays on -- it only ever
# deletes whole unused classes, and every reflective use is kept below. The
# optimiser buys a few percent more and can silently produce a binary that does
# not run, which is not a trade worth making for an application this size.
# ---------------------------------------------------------------------------
-dontoptimize

# ---------------------------------------------------------------------------
# okio, and a Java that is newer than this build's.
#
# okio arrives through androidx.datastore-preferences-core. Its AsyncTimeout
# watchdog is compiled against java.lang.Thread.Builder -- the virtual thread
# API added in Java 21 -- and picks it up reflectively at runtime when it is
# there. This build runs on JDK 17, where those three classes do not exist, so
# ProGuard reports three unresolved references and refuses to continue.
#
# Nothing is broken: okio checks for the API before using it and falls back to
# an ordinary thread. The warnings are the whole failure, so they are what is
# switched off, and only for okio.
# ---------------------------------------------------------------------------
-dontwarn okio.**

# ---------------------------------------------------------------------------
# A stack trace that can be read.
#
# Same reason as on Android: there is no crash reporter, so a trace copied out
# of a console by a person is the only channel there will ever be.
# ---------------------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Generic types survive to runtime. kotlinx.serialization reads them to build a
# serialiser for something like List<ChunkPack>; without Signature that type is
# gone by then and reading a deck fails with a missing-serialiser error.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---------------------------------------------------------------------------
# kotlinx.serialization.
#
# Generated serialisers are reached through a synthetic companion or a static
# serializer() method, and no call to either is visible in the bytecode. This is
# what makes the catalogue, the pack format, the settings backup and the update
# check parse in a shrunk build. The desktop application reads all four.
# ---------------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class dev.ikna.**$$serializer { *; }
-keepclassmembers class dev.ikna.** { *** Companion; }
-keepclasseswithmembers class dev.ikna.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Room and the bundled SQLite.
#
# On Android these ship consumer rules inside their own artifacts and this file
# would only hold a staler copy of them. ProGuard on the desktop gets no such
# thing: consumer rules are an Android Gradle plugin feature, so the same
# libraries arrive here unprotected.
#
# Two kinds of use are invisible. Room's generated IknaDatabase_Impl and its
# DAOs are built by name from generated code, and sqlite-bundled is a JNI
# binding -- a native method is bound by its exact name and signature, and
# renaming the Kotlin side of that pair fails with UnsatisfiedLinkError at the
# first query rather than at build time.
# ---------------------------------------------------------------------------
-keep class dev.ikna.data.db.** { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ---------------------------------------------------------------------------
# DataStore.
#
# The settings file is read and written through datastore-preferences-core,
# which reaches its own generated protobuf classes reflectively.
# ---------------------------------------------------------------------------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---------------------------------------------------------------------------
# Coroutines on the Swing thread.
#
# SwingDispatcherFactory is named in a META-INF/services file and nowhere else,
# so ProGuard has no reference to it at all. Without it Dispatchers.Main does
# not resolve and the window never gets its first frame.
# ---------------------------------------------------------------------------
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# The application's own entry point. jpackage launches it by name.
-keep class dev.ikna.desktop.MainKt { *; }
