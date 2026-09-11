// Root project build configuration
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
    }

    // Prohibit all uninstall tasks across all modules to prevent accidental loss of app data/state
    tasks.matching { it.name.contains("uninstall", ignoreCase = true) }.configureEach {
        doFirst {
            throw GradleException(
                "Refusing to run '$name': Uninstalling applications via Gradle is disabled on all devices (real or virtual) to prevent data loss. " +
                    "If you explicitly need to uninstall, use scripts/uninstall-app.sh.",
            )
        }
    }

    // Prohibit AGP connected test tasks (connectedAndroidTest, connectedDebugAndroidTest, connectedCheck, etc.)
    // because AGP's test runner automatically uninstalls both the test APK and target application APK
    // upon test completion across all connected devices (real or virtual).
    tasks.matching { it.name.startsWith("connected") }.configureEach {
        val allowConnectedTest = project.hasProperty("allowConnectedTest")
        doFirst {
            if (!allowConnectedTest) {
                throw GradleException(
                    "Refusing to run '$name': AGP connected instrumentation tests automatically uninstall applications " +
                        "after test completion, which wipes app data and credentials on all connected devices (real or virtual). " +
                        "To force running connected tests, pass -PallowConnectedTest=true.",
                )
            }
        }
    }
}



