plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
    }

    // A failed unit test prints its whole stack trace, so a failure seen only in CI shows its cause.
    tasks.withType<Test>().configureEach {
        testLogging {
            events(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    // No uninstall task in any module, so app data and state are never lost by accident.
    tasks.matching { it.name.contains("uninstall", ignoreCase = true) }.configureEach {
        doFirst {
            throw GradleException(
                "Refusing to run '$name': Uninstalling applications via Gradle is disabled on all devices (real or virtual) to prevent data loss. " +
                    "If you explicitly need to uninstall, use scripts/uninstall-app.sh.",
            )
        }
    }

    // No AGP connected test tasks (connectedAndroidTest, connectedDebugAndroidTest, connectedCheck
    // and others): AGP's test runner uninstalls the test APK and the app APK from every connected
    // device when the tests end.
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



