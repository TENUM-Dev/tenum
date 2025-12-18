import de.aaschmid.gradle.plugins.cpd.Cpd
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.cpd)
    alias(libs.plugins.kover)
    id("tenum.conventions.maven-publication")
    alias(libs.plugins.kotlin.multiplatform) apply false
    id("tenum.conventions.common") apply false
    id("tenum.conventions.mpplib") apply false
}

group = "ai.tenum"

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
                    // Skip generated sources (e.g., BuildConfig) to avoid extra task dependencies
                    ?.filterNot { it.toString().replace("\\", "/").contains("/build/generated/") }
                    ?: emptyList()
            }.distinct()
    }

tasks.named<Cpd>("cpdCheck") {
    description = "Runs PMD CPD across Kotlin sources in all modules."
    language = "kotlin"

    setSource(files(kotlinSourceDirs))
    include("**/*.kt")
    exclude("**/build/**", "**/.gradle/**", "**/node_modules/**", "**/generated/sources/buildConfig/**")

    // Ensure generated BuildConfig sources exist before CPD runs
    subprojects.forEach { sub ->
        dependsOn(sub.tasks.matching { it.name == "generateBuildConfigClasses" })
    }
}

kover {
    merge {
        // Apply Kover to all subprojects and merge their reports into this root project
        // exept test/integration and test/performance
        subprojects {
            setOf(
                "integration",
                "performance",
                "tests",
            ).contains(
                it.name,
            ).not()
        }
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

// Provide standard Java lifecycle-style aggregates for tooling (CodeQL, IDEs) in a KMP build
val aggregatedClasses =
    tasks.register("classes") {
        group = "build"
        description = "Aggregate class generation across all subprojects and targets."
    }

val aggregatedTestClasses =
    tasks.register("testClasses") {
        group = "verification"
        description = "Aggregate test class generation across all subprojects and targets."
    }

// Wire dependencies after all projects are evaluated so that task discovery works reliably
gradle.projectsEvaluated {
    aggregatedClasses.configure {
        dependsOn(
            subprojects.flatMap { sub ->
                sub.tasks.matching { task ->
                    task.name.endsWith("Classes") && !task.name.contains("Test")
                }
            },
        )
    }
    aggregatedTestClasses.configure {
        dependsOn(
            subprojects.flatMap { sub ->
                sub.tasks.matching { task ->
                    task.name.endsWith("TestClasses")
                }
            },
        )
    }
}