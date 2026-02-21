// Root project build configuration
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Override JVM toolchain for all subprojects to accept any JDK 21 vendor,
// not just JetBrains. AGP 9.0+/Kotlin 2.3+ auto-configures vendor=JetBrains
// which requires downloading JBR. This override runs after all plugins are
// applied (afterEvaluate) and clears the vendor restriction so any JDK 21 works.
subprojects {
    afterEvaluate {
        extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)
            ?.jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
                vendor.set(JvmVendorSpec.matching(""))
            }
    }
}

