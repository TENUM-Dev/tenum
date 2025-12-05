import org.gradle.kotlin.dsl.base

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

base {
    archivesName.set("$group.$name")
}

tasks {
    withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

kotlin {
    js {
        compilerOptions {
            freeCompilerArgs.add("-Xes-long-as-bigint")
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers")
}
