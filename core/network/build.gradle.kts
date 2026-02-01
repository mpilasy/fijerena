plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.njarasoa.fijerena.core.network"
    compileSdk = 36
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":core:player"))
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
