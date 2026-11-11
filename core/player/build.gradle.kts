plugins {
    alias(libs.plugins.android.library)
    // No @Composable code here, but the domain types below go into composition in every list.
    // Without the Compose compiler over this module they have no stability metadata and are
    // inferred unstable downstream, so list items recompose. The plugin makes the compiler emit
    // @StabilityInferred for them.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.njarasoa.fijerena.core.player"
    compileSdk = 37

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
        baseline = file("lint-baseline.xml")
        disable.add("UnsafeOptInUsageError")
    }
    testOptions {
        unitTests {
            // StreamingPlaybackServiceRecoveryTest builds the service on the JVM: its Handler,
            // Log and SystemClock calls must return defaults instead of throwing "not mocked".
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Compose runtime only, no UI: the compiler plugin above needs it to emit the stability
    // annotations. api() so the annotations resolve for consumers.
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.runtime:runtime")

    // Media3 (ExoPlayer)
    api(libs.bundles.media)

    // FFmpeg software decoding of AC3, EAC3, DTS, TrueHD and more: Jellyfin's pre-built Media3
    // FFmpeg decoder (Maven Central).
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.networking)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
