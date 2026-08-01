plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.njarasoa.fijerena.core.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Core modules
    implementation(project(":core:network"))
    implementation(project(":core:player"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Room (needed for EpgIndexDatabase access in EpgBrowserViewModel)
    implementation(libs.room.runtime)

    // Image Loading
    // coil-compose pulls in org.jetbrains.compose.* (Compose Multiplatform) transitively, which
    // ships its own copy of androidx.compose.foundation.layout classes (e.g. FlowRow) under an
    // older Compose version than this app's real androidx BOM. The duplicate class wins at dex
    // time in some builds, causing NoSuchMethodError at runtime — exclude it, this app is
    // Android-only and already gets the real foundation-layout from the androidx BOM directly.
    implementation(libs.coil.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.components")
    }
    implementation(libs.coil.network.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
