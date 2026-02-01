# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep security-crypto classes
-keep class androidx.security.crypto.** { *; }

# Keep kotlinx.serialization classes
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# Keep data classes for serialization
-keep @kotlinx.serialization.Serializable class * { *; }
