# R8 rules.
#
# Release builds shrink and optimise as of the press epoch, which means this file
# runs for the first time. Everything here names what it protects and why: a keep
# rule nobody can explain is a rule nobody will ever dare delete.
#
# The failure mode this file exists to prevent is specific. R8 removes and renames
# whatever it cannot see being used, and it cannot see three kinds of use:
# reflection by name, native code calling back into Java, and a class name that
# was written into storage weeks ago and is being resolved now. All three exist in
# this app. A missing rule is a crash that happens only in release builds, only on
# somebody else's phone, and usually only in the feature they just turned on.

# ---------------------------------------------------------------------------
# Debuggable crash reports.
#
# There is no crash reporter in this app, and there will not be one: a stack
# trace read out by a person from a bug report is the only channel. Keeping line
# numbers costs a few kilobytes and is the difference between a report that can
# be acted on and one that cannot.
# ---------------------------------------------------------------------------
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# Annotations, generics and inner-class metadata.
#
# kotlinx.serialization reads generic types at runtime to build serialisers for
# things like List<ChunkPack>. Without Signature that type is gone by then and
# deserialising a deck fails at runtime with an error about a missing serialiser.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ---------------------------------------------------------------------------
# kotlinx.serialization.
#
# Generated serialisers are found through a synthetic companion or a static
# serializer() method, neither of which R8 can see being called. This is the
# reason the pack format, the settings backup and the update manifest all parse.
# ---------------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class dev.ikna.**$$serializer { *; }
-keepclassmembers class dev.ikna.** { *** Companion; }
-keepclasseswithmembers class dev.ikna.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# JNI.
#
# A native method is bound by its exact name and signature. Renaming the Java
# side of that pair does not fail at build time and does not fail at startup: it
# fails with UnsatisfiedLinkError the moment the feature is used. Both libraries
# here are optional features, which is the worst case for noticing it late.
#
#  - sherpa-onnx is the offline speech runtime, loaded when voice is switched on.
#  - zstd-jni decompresses Anki .apkg collections, on import only.
# ---------------------------------------------------------------------------
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**
-keep class com.github.luben.zstd.** { *; }
-dontwarn com.github.luben.zstd.**

# commons-compress can hand a zip entry to XZ or Brotli. Neither library is a
# dependency here, so R8 warns about the classes it cannot find and fails the
# release build. .apkg files are deflate or zstd, so the missing branches are
# never reached.
-dontwarn org.tukaani.xz.**
-dontwarn org.brotli.**

# ---------------------------------------------------------------------------
# WorkManager.
#
# A scheduled job is a class name in WorkManager's own database. The daily plan
# worker, the reminder and the export were all scheduled by a build that ran
# before this one; the name stored then has to still resolve now, or the daily
# plan silently stops being built for everybody who upgrades.
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# ---------------------------------------------------------------------------
# Not listed on purpose.
#
# Room, Compose, DataStore and commons-compress ship consumer rules inside their
# own artifacts, so copying rules for them here would only create a second,
# staler copy. If something in one of those breaks under R8, the fix belongs in a
# version bump, not in this file.
# ---------------------------------------------------------------------------
