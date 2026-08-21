# Portico release rules.
#
# The release build shrinks and obfuscates. Everything below exists because
# something in the app reaches for a class by name at runtime, which R8 cannot
# see: serialization reflection, Firebase model mapping, and the Clerk SDK's
# own internals. Anything not listed here is fair game to strip.

# ---------------------------------------------------------------- kotlinx.serialization
# The whole persistence layer round-trips through @Serializable classes. R8
# cannot see the generated serializers from the call site, so both the
# companion accessor and the synthetic serializer classes must survive.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.portico.android.**$$serializer { *; }
-keepclassmembers class com.portico.android.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.portico.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Domain models are mapped to and from Firestore documents by field name, so
# their members must keep their original names.
-keep class com.portico.android.domain.** { *; }
-keep class com.portico.android.data.PorticoSnapshot { *; }

# ---------------------------------------------------------------- Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore maps documents onto classes reflectively.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Crashlytics: keep line numbers and source names or every stack trace in the
# console is unreadable. The mapping file is uploaded by the Gradle plugin.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------- Clerk
-keep class com.clerk.** { *; }
-dontwarn com.clerk.**

# ---------------------------------------------------------------- Compose
# AGP ships Compose rules, but the runtime reads these annotations reflectively
# in a few places and stripping them breaks composition tracing.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------------------------------------------------------------- Kotlin
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class ** {
    @kotlin.jvm.JvmStatic *;
}
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**

# ---------------------------------------------------------------- OkHttp / conscrypt
# Pulled in transitively; the warnings are for optional platform integrations
# that Android does not ship.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
