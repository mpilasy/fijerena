plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    // For the back-stack tests' graph (composable destinations).
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.njarasoa.fijerena.core.navigation"
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
    }
    testOptions {
        unitTests {
            // Robolectric back-stack tests (SearchBackStackTest).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Kotlinx Serialization for type-safe navigation
    api(libs.kotlinx.serialization.json)

    api(libs.androidx.navigation.compose)

    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.navigation.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
