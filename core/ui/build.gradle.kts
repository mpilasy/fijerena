plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.njarasoa.fijerena.core.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    lint {
        baseline = file("lint-baseline.xml")
        // values-fr and values-mg are both deliberately partial. Android falls back to the default
        // values/ string per missing key, so an untranslated string renders English rather than
        // breaking — a completeness signal, not a build blocker.
        warning.add("MissingTranslation")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests {
            // SyncManagerSocketTest runs SyncManager on the JVM: its Log calls must return
            // defaults instead of throwing "not mocked".
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.zxing.core)
    implementation(libs.youtube.player.core)
    // Reads the open-source licence list the apps generate at build time (OpenSourceLicenses).
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:network"))
    implementation(project(":core:player"))
    implementation(project(":core:navigation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // The game reminders' notification permission request (rememberGameReminderPermission).
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Room (needed for EpgIndexDatabase access in EpgBrowserViewModel)
    implementation(libs.room.runtime)

    // coil-compose brings in org.jetbrains.compose.* (Compose Multiplatform), which ships its own
    // copy of androidx.compose.foundation.layout classes (e.g. FlowRow) from an older Compose. The
    // duplicate can win at dex time and cause NoSuchMethodError at runtime; the app is Android-only
    // and gets foundation-layout from the androidx BOM.
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
    // TMDB serves some title logos as SVG only, or ranks an SVG first (Silo).
    implementation(libs.coil.svg)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
