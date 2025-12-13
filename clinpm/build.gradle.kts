import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeSetupTask
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

plugins {
    // Provides npm/node tasks (npmInstall, npm_run_*)
    id("com.github.node-gradle.node")
    base
}

repositories {
    mavenCentral()
}

// We need the JS artifacts from :cli
evaluationDependsOn(":cli")

node {
    // Rely on system Node to avoid deep nested Gradle-managed node dirs on Windows
    download.set(false)
    nodeProjectDir.set(projectDir)
}

// Gradle 9 workaround: nodeSetup output tracking can fail on Windows; disable state tracking
tasks.withType<NodeSetupTask>().configureEach {
    doNotTrackState("Node binary download/cache is not incremental")
}

val npmInstall = tasks.named<NpmInstallTask>("npmInstall")

// Resolve the actual JS production task in :cli after projects are evaluated to avoid early lookup issues.
val cliJsBuild: TaskProvider<Task> =
    tasks.register("cliJsBuild") {
        description = "Build JS artifacts in :cli for npm bundling"
    }

gradle.projectsEvaluated {
    val cliProject = project(":cli")
    val target =
        listOf(
            // KMP 2.0 style
            "jsProductionExecutableCompileSync",
            "jsProductionLibraryCompileSync",
            // Legacy Kotlin/JS IR names
            "compileProductionExecutableKotlinJs",
            "compileProductionLibraryKotlinJs",
        ).firstNotNullOfOrNull { name ->
            try {
                cliProject.tasks.named(name)
            }catch (_: Exception) {
                print("Not found task: $name")
                null
            }
        } ?: cliProject.tasks.named("build")

    cliJsBuild.configure {
        dependsOn(target)
    }
}

val npmBundleInstall = tasks.register<NpmTask>("npmBundleInstall") {
    description = "Bundle the CLI JS and install the package globally via npm"
    dependsOn(npmInstall)
    dependsOn(cliJsBuild)
    args.set(listOf("run", "build:bundle:install"))
    workingDir.set(projectDir)
}

tasks.register("installLocal") {
    group = "distribution"
    description = "Build JS in :cli, bundle the npm package, and install it locally."
    dependsOn(npmBundleInstall)
}
