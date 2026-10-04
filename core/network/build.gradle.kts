import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Room schema history for the databases that hold user data (xtream_v2.db, providers.db): one JSON
// per version under schemas/, committed. CI fails when a build regenerates one that wasn't committed.
// See docs/plans/archive/20261001_rock-solid-stability-resilience-plan.md → F-33.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "org.njarasoa.fijerena.core.network"
    compileSdk = 36
    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
    lint {
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests {
            // MediaRepository starts a HandlerThread; without this the android.jar stubs throw
            // "Method getLooper in android.os.HandlerThread not mocked" and every test in
            // MediaRepositoryTest fails before reaching its assertions.
            isReturnDefaultValues = true
        }
    }
    // MigrationTestHelper (XtreamDatabaseUpgradeTest) reads the exported schemas from the test APK's assets.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    api(project(":core:player"))
    implementation("androidx.security:security-crypto:1.1.0")
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // DocumentFile for local media scanning (SAF)
    implementation("androidx.documentfile:documentfile:1.1.0")

    // SMB2/3 client for network share access
    implementation("com.hierynomus:smbj:0.15.0")

    // Ktor HTTP client for Jellyfin API
    implementation(libs.bundles.networking)

    implementation(libs.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Bundled SQLite with FTS5 support (system SQLite may lack FTS5 on some OEM builds)
    implementation(libs.sqlite.android)

    api(libs.paging.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented: Room migration verification (MigrationTestHelper needs a real SQLite via
    // instrumentation — no JVM/Robolectric equivalent covers actual on-device migration behavior).
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
