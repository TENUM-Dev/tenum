rootProject.name = "tenum"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

includeBuild("build-logic")

include(
    ":lua",
    ":cli",
    ":clinpm",
)
