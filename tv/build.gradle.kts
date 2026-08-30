import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

// The composeBom pin below is supposed to keep every androidx.compose.* artifact at 1.8.3 (see
// the comment on composeBom in libs.versions.toml: tv-foundation:1.0.0-alpha10's TvLazyListState
// calls LazyLayoutPrefetchState.schedulePrefetch on every scroll, a method Compose 1.9 removed).
// But implementation(platform(bom)) only *recommends* that version — plain conflict resolution
// still lets a published "prevents a regression in Overscroll" constraint drag
// androidx.compose.foundation/ui/animation up to 1.10.0 on the actual runtime classpath, crashing
// TvLazyColumn/TvLazyRow on first scroll. useVersion (a hard force) actually wins where a
// dependency-level `strictly` constraint did not — force overrides other constraints outright
// instead of merely failing loudly when they disagree.
// Scoped to exactly the artifacts implicated in that constraint's chain (foundation, ui,
// animation, and their -android publication variants) rather than every androidx.compose.*
// group: material/material3 need to stay off this list (material3 is intentionally pinned
// separately, to 1.4.0 rather than the BOM's 1.3.2 — see the composeMaterial3 alias), and several
// other compose artifacts (material-icons-extended/core, runtime-annotation, ...) simply don't
// publish a 1.8.3 release at all — forcing every androidx.compose.* module here 404s on those.
val composeArtifactsToPin =
    setOf(
        "foundation", "foundation-android",
        "foundation-layout", "foundation-layout-android",
        "ui", "ui-android",
        "ui-graphics", "ui-graphics-android",
        "ui-unit", "ui-unit-android",
        "ui-util", "ui-util-android",
        "ui-tooling", "ui-tooling-android",
        "ui-tooling-data", "ui-tooling-data-android",
        "ui-tooling-preview", "ui-tooling-preview-android",
        "animation", "animation-android",
        "animation-core", "animation-core-android",
    )
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group.startsWith("androidx.compose.") && requested.name in composeArtifactsToPin) {
            useVersion("1.8.3")
            because("pin to composeBom 2025.06.01's Compose 1.8.3 — tv-foundation:1.0.0-alpha10 needs pre-1.9 Compose")
        }
    }
}

android {
    namespace = "org.njarasoa.fijerena"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.njarasoa.fijerena"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"

        val gitHash =
            try {
                providers
                    .exec {
                        commandLine("git", "rev-parse", "--short", "HEAD")
                    }.standardOutput.asText
                    .get()
                    .trim()
            } catch (e: Exception) {
                "unknown"
            }

        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date())
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
        }
        jniLibs {
            keepDebugSymbols +=
                setOf(
                    "**/libandroidx.graphics.path.so",
                    "**/libffmpegJNI.so",
                    "**/libsqlite3x.so",
                )
        }
    }
}

dependencies {
    implementation(project(":core:player"))
    implementation(project(":core:navigation"))
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)

    // Compose & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // TV (uses androidx.tv.material instead of material3)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

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
    // Dominant-color extraction for AmbientBackdrop's pre-API-31 fallback (no RenderEffect blur)
    implementation(libs.androidx.palette)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/**
 * Gradle task to deploy TV app to Shield/Sony TV via ADB.
 *
 * Usage:
 *   1. Set TV IP in gradle.properties: tv.ip.address=192.168.1.100
 *   2. Run: ./gradlew deployToShield
 *
 * This task will:
 *   - Connect to your TV via ADB
 *   - Build and install the TV app
 */
tasks.register("deployToShield") {
    group = "deployment"
    description = "Connect to Shield/Sony TV via ADB and install the TV app"

    dependsOn("assembleDebug")

    doLast {
        val tvIpAddress =
            project.findProperty("tv.ip.address") as? String
                ?: throw org.gradle.api.GradleException(
                    "TV IP address not set. Add 'tv.ip.address=YOUR_TV_IP' to gradle.properties",
                )

        println("🔌 Connecting to TV at $tvIpAddress...")

        // Connect to TV via ADB
        val connectProcess =
            ProcessBuilder("adb", "connect", "$tvIpAddress:5555")
                .redirectErrorStream(true)
                .start()
        connectProcess.waitFor()

        println("✅ Connected to TV")
        println("📦 Installing TV app...")

        // Install the built APK
        val installProcess =
            ProcessBuilder(
                "adb",
                "install",
                "-r",
                "build/outputs/apk/debug/tv-debug.apk",
            ).redirectErrorStream(true)
                .start()

        val exitCode = installProcess.waitFor()
        if (exitCode == 0) {
            println("🎉 TV app deployed successfully!")
        } else {
            throw org.gradle.api.GradleException("Failed to install APK. Exit code: $exitCode")
        }
    }
}
