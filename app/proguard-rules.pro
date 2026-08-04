-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class dev.ikna.**$$serializer { *; }
-keepclassmembers class dev.ikna.** {
    *** Companion;
}
