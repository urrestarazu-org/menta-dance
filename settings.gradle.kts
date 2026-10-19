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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "menta-dance"

include(
    "api:shared",
    "api:auth",
    "api:billing",
    "api:virtual",
    "api:physical",
    "api:app",
    "android",
    "bff"
)
