# Default Android Compose + ML Kit + Ktor friendly rules.
# Most modern dependencies ship their own consumer rules; we only add app-specific keeps here.

# Kotlinx serialization (preserve @Serializable companions)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.ultraprocessed.**$$serializer { *; }
-keepclassmembers class com.ultraprocessed.** {
    *** Companion;
}
-keepclasseswithmembers class com.ultraprocessed.** {
    kotlinx.serialization.KSerializer serializer(...);
}
