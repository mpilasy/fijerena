pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// foojay-resolver removed: toolchain auto-download disabled, local JDK detected automatically
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack for community builds
        maven { url = uri("https://jitpack.io") }
        // Jellyfin releases for pre-built FFmpeg extension
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/releases/") }
    }
}

rootProject.name = "fijerena"
include(":mobile")
include(":tv")
include(":core:player")
include(":core:navigation")
include(":core:data")
include(":core:network")
include(":core:ui")
