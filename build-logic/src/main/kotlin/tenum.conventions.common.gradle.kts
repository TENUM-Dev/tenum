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
