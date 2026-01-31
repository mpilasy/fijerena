# Media3 (ExoPlayer)
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }

# Kotlinx Serialization
-keepattrs *Annotation*
-keep class kotlinx.serialization.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}

# Ktor Client
-keep class io.ktor.** { *; }
-keep interface io.ktor.** { *; }

# Kotlinx Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep service class
-keep class com.example.firstvideoplayer.core.player.service.** { *; }
