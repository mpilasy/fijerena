import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

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

android {
    namespace = "org.njarasoa.fijerena"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.njarasoa.fijerena"
        minSdk = 30
        targetSdk = 35
        // Both apps' version, in gradle.properties: a release tag must match it (release.yml).
        versionCode = providers.gradleProperty("fijerena.versionCode").get().toInt()
        versionName = providers.gradleProperty("fijerena.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

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

    // Release signing (release plan D7): the key from FIJERENA_* environment variables in CI, or from
    // keystore.properties (never committed) on a developer's machine. Without either, a release
    // build is unsigned.
    signingConfigs {
        val keystoreProperties =
            rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
                Properties().apply { file.inputStream().use { load(it) } }
            }

        fun signingValue(
            env: String,
            key: String,
        ): String? = providers.environmentVariable(env).orNull ?: keystoreProperties?.getProperty(key)
        signingValue("FIJERENA_KEYSTORE_FILE", "storeFile")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = signingValue("FIJERENA_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("FIJERENA_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("FIJERENA_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Emulators are x86/x86_64; without these the debug build ran its ARM libraries
            // under binary translation there. Release stays ARM-only (every real device).
            ndk {
                abiFilters += listOf("x86", "x86_64")
            }
        }
        release {
            signingConfig = signingConfigs.findByName("release")
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
    testOptions {
        unitTests {
            // Robolectric Compose tests read the app's resources (strings, themes).
            isIncludeAndroidResources = true
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
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
    implementation(project(":core:network"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // TV (uses androidx.tv.material instead of material3)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

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

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // Compose UI tests on the JVM (Robolectric), no device: connectedAndroidTest uninstalls the
    // app and wipes the device's data (docs/plans/20260914_codebase-robustness-plan.md, Phase 5).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
