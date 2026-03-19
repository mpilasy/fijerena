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
}

// Collect all APKs into build/outputs/apk/ after every assemble.
// e.g. tv-debug.apk → build/outputs/apk/fijerena-tv-debug.apk
subprojects {
    afterEvaluate {
        if (plugins.hasPlugin("com.android.application")) {
            val rootApkDir = rootProject.file("build/outputs/apk")
            val moduleApkDir = project.file("build/outputs/apk")
            tasks.matching { it.name.startsWith("assemble") && !it.name.contains("Test") }.configureEach {
                doLast {
                    rootApkDir.mkdirs()
                    moduleApkDir.walkTopDown()
                        .filter { it.extension == "apk" && !it.name.contains("release") }
                        .forEach { apk ->
                            apk.copyTo(File(rootApkDir, "fijerena-${apk.name}"), overwrite = true)
                        }
                }
            }
        }
    }
}
