rootProject.name = "build-logic"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        // Needed so precompiled script plugins in this included build can resolve Kotlin plugins without hardcoding versions in each script
        val kotlinVersion = "2.3.10"
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
        // External plugins used by precompiled convention scripts
        //id("org.zkovari.changelog") version "0.4.0"
        id("org.nosphere.gradle.github.actions") version "1.4.0"
        id("com.palantir.docker") version "0.37.0"
        id("org.danilopianini.npm.publish") version "4.1.10"
    }
}

dependencyResolutionManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // Reuse the root project's version catalog for plugins and dependencies
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
