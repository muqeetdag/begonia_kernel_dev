/*
 * SablePureBlack — by @drkmuqeett
 * Gradle settings (Kotlin DSL)
 *
 * Root project name and module list for the SablePureBlack LSPosed module.
 */

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
        // Xposed API 82 — compileOnly framework classes, never packaged.
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "SablePureBlack"
include(":app")
