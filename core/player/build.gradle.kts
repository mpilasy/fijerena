plugins {
    alias(libs.plugins.android.library)
    // No @Composable code lives here, but the domain types below cross into composition in every
    // list in the app. Without the Compose compiler running over this module they carry no
    // stability metadata and are inferred *unstable* downstream, which is what forces list items
    // to recompose. Applying the plugin makes the compiler emit @StabilityInferred for them.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.njarasoa.fijerena.core.player"
    compileSdk = 36

    defaultConfig {
        minSdk = 30

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    lint {
        disable.add("UnsafeOptInUsageError")
    }
}

dependencies {
    // Compose runtime only — no UI. The compiler plugin above needs it to emit the stability
    // annotations onto the domain types; api() so the annotations resolve for consumers.
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.runtime:runtime")

    // Media3 (ExoPlayer) - Latest stable 1.7.1
    api(libs.bundles.media)

    // FFmpeg extension for software decoding of AC3, EAC3, DTS, TrueHD, etc.
    // Pre-built Media3 FFmpeg decoder from Jellyfin (Maven Central)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+2")

    // Kotlinx Coroutines
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)

    // Networking & Serialization
    implementation(libs.bundles.networking)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
