import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries)
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
    compileSdk = 37

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
        // Each build type names itself in the manifest (meta-data fijerena.buildType): Live Sync
        // shows it in the device list (device builds plan). localRelease sets its own after initWith.
        debug {
            manifestPlaceholders["fijerenaBuildType"] = "debug"
            // Emulators are x86/x86_64; without these the debug build ran its ARM libraries
            // under binary translation there. Release stays ARM-only (every real device).
            ndk {
                abiFilters += listOf("x86", "x86_64")
            }
        }
        release {
            manifestPlaceholders["fijerenaBuildType"] = "release"
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // The release code, not debuggable, signed with the debug key: it installs over a debug
        // build and keeps the app's data. Only for measuring on our own devices and emulators
        // (scripts/deploy-*.sh --release); never published. Profileable from the shell, so
        // Perfetto still works (src/localRelease/AndroidManifest.xml).
        create("localRelease") {
            initWith(getByName("release"))
            manifestPlaceholders["fijerenaBuildType"] = "localRelease"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            ndk {
                abiFilters += listOf("x86", "x86_64")
            }
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

    // TV Material (androidx.tv.material); Material 3 for dialogs and controls inside TV screens.
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // coil-compose pulls in Compose Multiplatform (org.jetbrains.compose.*), which ships its own older
    // copy of androidx.compose.foundation.layout classes (FlowRow). That copy can win at dex time and
    // cause NoSuchMethodError, so exclude it: this Android-only app gets foundation-layout from the BOM.
    implementation(libs.coil.compose) {
        exclude(group = "org.jetbrains.compose.foundation")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.ui")
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.material3")
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.compose.components")
    }
    // Dominant colour for AmbientBackdrop's fallback below API 31 (no RenderEffect blur).
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
    // The Settings focus tests seed the settings database before the screen opens.
    testImplementation(libs.room.runtime)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Settings → About → Open-source licences: every library this app ships, from its dependencies at
// build time, plus the bundled fonts (config/aboutlibraries).
aboutLibraries {
    // No network at build time: licence texts come from config/aboutlibraries/licenses.
    offlineMode = true
    collect {
        configPath = file("../config/aboutlibraries")
    }
}
