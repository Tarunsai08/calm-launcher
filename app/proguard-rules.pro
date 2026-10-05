# Calm Launcher R8 rules. Most libraries (Compose, Hilt, Room, kotlinx.serialization) ship
# their own consumer rules; these cover our own reflective/serialized types.

# kotlinx.serialization: keep generated serializers for our @Serializable models
# (settings file and backup documents must stay readable across versions).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.calmlauncher.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.calmlauncher.**$$serializer { *; }
-keepclassmembers class com.calmlauncher.** {
    *** Companion;
}
-keepclasseswithmembers class com.calmlauncher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enum names are persisted in settings and backups.
-keepclassmembers enum com.calmlauncher.** { *; }

# Strip verbose logging in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
