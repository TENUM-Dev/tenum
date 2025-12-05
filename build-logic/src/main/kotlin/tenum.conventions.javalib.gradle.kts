import org.gradle.kotlin.dsl.base
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

if (System.getenv("PACKAGE_VERSION") != null) {
    project.version = System.getenv("PACKAGE_VERSION")
}
base {
    archivesName.set("$group.$name")
}

val ideaActive = System.getProperty("idea.active") == "true"
val compileNative = findProperty("compileNative") == "true"

repositories {
    mavenCentral()
}

tasks {
    withType<Jar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
    withType<KotlinCompilationTask<*>> {
        compilerOptions {
            optIn.add("kotlin.RequiresOptIn")
            freeCompilerArgs.add("-Xexpect-actual-classes")
            // allWarningsAsErrors = true
        }
    }
    withType<AbstractArchiveTask> {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
