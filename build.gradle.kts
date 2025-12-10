import de.aaschmid.gradle.plugins.cpd.Cpd
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.cpd)
    alias(libs.plugins.kover)
    id("tenum.conventions.maven-publication")
    id("tenum.conventions.changelog")
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("tenum.conventions.common") apply false
    id("tenum.conventions.mpplib") apply false
}

group = "ai.plantitude.luak"

repositories {
    mavenCentral()
}

private val kotlinSourceDirs =
    providers.provider {
        subprojects
            .flatMap { project ->
                project.extensions
                    .findByType<KotlinMultiplatformExtension>()
                    ?.sourceSets
                    ?.flatMap { sourceSet -> sourceSet.kotlin.srcDirs }
                    ?.filter { it.exists() }
                    ?: emptyList()
            }.distinct()
    }

tasks.named<Cpd>("cpdCheck") {
    description = "Runs PMD CPD across Kotlin sources in all modules."
    language = "kotlin"

    setSource(files(kotlinSourceDirs))
    include("**/*.kt")
    exclude("**/build/**", "**/.gradle/**", "**/node_modules/**", "**/generated/sources/buildConfig/**")

    // BuildConfig sources for :cli are generated; ensure they are created before CPD runs
    dependsOn(project(":cli").tasks.named("generateBuildConfig"))
}

kover {
    merge {
        // Apply Kover to all subprojects and merge their reports into this root project
        subprojects()
    }
    reports {
        total {
            xml {
                onCheck.set(true)
                xmlFile.set(layout.buildDirectory.file("reports/kover/coverage.xml"))
            }
            html {
                onCheck.set(false)
            }
        }
    }
}

subprojects {
    plugins.withType<LifecycleBasePlugin> {
        tasks.named("check") {
            dependsOn(rootProject.tasks.named("cpdCheck"))
        }
    }
}
