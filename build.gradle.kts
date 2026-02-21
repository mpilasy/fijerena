// Root project build configuration
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("androidx.media3:media3-exoplayer:1.5.1")
            force("androidx.media3:media3-exoplayer-hls:1.5.1")
            force("androidx.media3:media3-exoplayer-dash:1.5.1")
            force("androidx.media3:media3-session:1.5.1")
            force("androidx.media3:media3-ui:1.5.1")
            force("androidx.media3:media3-common:1.5.1")
            force("androidx.media3:media3-datasource:1.5.1")
            force("androidx.media3:media3-decoder:1.5.1")
            force("androidx.media3:media3-extractor:1.5.1")
            force("androidx.media3:media3-container:1.5.1")
            force("androidx.media3:media3-database:1.5.1")
        }
    }
}
