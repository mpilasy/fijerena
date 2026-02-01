# Keep kotlinx.serialization types for navigation
-keep class org.njarasoa.fijerena.core.navigation.** { *; }
-keepclassmembers class org.njarasoa.fijerena.core.navigation.** {
    *;
}

# Keep serialization annotations
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# kotlinx-serialization-json specific
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep sealed classes for navigation
-keep class * extends org.njarasoa.fijerena.core.navigation.Screen
