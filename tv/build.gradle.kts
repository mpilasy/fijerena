import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.njarasoa.fijerena"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.njarasoa.fijerena"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val gitHash = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date())
        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES")
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
    implementation(libs.androidx.appcompat)

    // Compose & UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // TV (uses androidx.tv.material instead of material3)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Image Loading
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    // Lifecycle & ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
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
        val tvIpAddress = project.findProperty("tv.ip.address") as? String
            ?: throw org.gradle.api.GradleException(
                "TV IP address not set. Add 'tv.ip.address=YOUR_TV_IP' to gradle.properties"
            )

        println("🔌 Connecting to TV at $tvIpAddress...")

        // Connect to TV via ADB
        val connectProcess = ProcessBuilder("adb", "connect", "$tvIpAddress:5555")
            .redirectErrorStream(true)
            .start()
        connectProcess.waitFor()

        println("✅ Connected to TV")
        println("📦 Installing TV app...")

        // Install the built APK
        val installProcess = ProcessBuilder(
            "adb", "install", "-r", "build/outputs/apk/debug/tv-debug.apk"
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