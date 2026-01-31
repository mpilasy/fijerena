plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yourname.xtreamclient"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.yourname.xtreamclient"
        minSdk = 21 // Works perfectly on Nvidia Shield & older Sony/TCL TVs
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core Android TV & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback) // Still needed for some TV infrastructure
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0-rc02")

    // Networking (Xtream API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image Loading (For Movie Posters)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Video Playback (Media3 ExoPlayer)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1") // For .m3u8 streams
    implementation("androidx.media3:media3-exoplayer-rtsp:1.4.1") // Sometimes used in IPTV
}