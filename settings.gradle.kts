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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "AntigravityMobile"

include(":app")
include(":core:contracts")
include(":core:chroot-runtime")
include(":core:account-manager")
include(":core:acp-client")
include(":core:root-control")
include(":core:accessibility-engine")
include(":core:android-mcp")
include(":feature:workspace-ui")
include(":feature:overlay-voice")
include(":feature:automation-security")
