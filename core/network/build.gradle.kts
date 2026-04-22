import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "org.njarasoa.fijerena.core.network"
    compileSdk = 36
    defaultConfig {
        minSdk = 30

        val tmdbApiKey =
            runCatching {
                val props = Properties()
                rootProject.file("local.properties").inputStream().use { props.load(it) }
                props.getProperty("TMDB_API_KEY") ?: ""
            }.getOrDefault("")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
    }
    buildFeatures {
        buildConfig = true
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

    // DocumentFile for local media scanning (SAF)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // SMB2/3 client for network share access
    implementation("com.hierynomus:smbj:0.13.0")

    // Ktor HTTP client for Jellyfin API
    implementation(libs.bundles.networking)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Bundled SQLite with FTS5 support (system SQLite may lack FTS5 on some OEM builds)
    implementation(libs.sqlite.android)

    // Paging
    api(libs.paging.runtime)

    // Google Drive API for settings sync
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive) {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
