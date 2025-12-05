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
    maven {
        url = uri("https://git.plantiwork.i234.me/api/packages/Plantitude.ai/maven")
        credentials(HttpHeaderCredentials::class) {
            name = "Authorization"
            value = "token " +
                System.getenv("PLANTITUDE_PACKAGE_READ_TOKEN").let {
                    if (it.isNullOrEmpty()) {
                        throw IllegalStateException("PLANTITUDE_PACKAGE_READ_TOKEN environmentVariable not set")
                    } else {
                        it
                    }
                }
        }
        authentication {
            create<HttpHeaderAuthentication>("header")
        }
    }
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
