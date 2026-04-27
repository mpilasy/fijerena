plugins {
    alias(libs.plugins.android.library)
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
