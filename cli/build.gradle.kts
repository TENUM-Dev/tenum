import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

plugins {
    id("tenum.conventions.common")
    alias(libs.plugins.shadow)
    distribution
    id("com.github.gmazzo.buildconfig") version "5.7.1"
}

fun KotlinNativeTargetWithHostTests.configureTarget() =
    binaries {
        executable {
            entryPoint = "ai.tenum.cli.main"
        }
    }

buildConfig {
    // forces the class name. Defaults to 'BuildConfig'
    packageName("ai.plantitude.runtime.products.tdm")
    buildConfigField("APP_NAME", project.name)
    buildConfigField("APP_VERSION", provider { "\"${project.version}\"" })
    buildConfigField("BUILD_TIME", System.currentTimeMillis())
}

kotlin {
    val os =
        org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
            .getCurrentOperatingSystem()
    if (os.isWindows) {
        mingwX64 { configureTarget() }
        linuxX64 { configureTarget() }
    } else if (os.isLinux) {
        linuxX64 { configureTarget() }
    } else if (os.isMacOsX) {
        macosX64 { configureTarget() }
        macosArm64 { configureTarget() }
    }
    applyDefaultHierarchyTemplate()

    js {
        generateTypeScriptDefinitions()
        binaries.library()
        nodejs {
            testTask {
                useMocha {
                    timeout = "60000"
                }
            }
        }
    }

        val jvmTarget =
        jvm {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            binaries {
                executable {
                    mainClass.set("ai.tenum.cli.MainKt")
                }
            }
        }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.appdirs)
                implementation(libs.github.ajalt.clikt)
                implementation(libs.okio)
                implementation(libs.ktmath)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.collections.immutable)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.okio.fakefilesystem)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions)
                implementation(libs.bundles.kotlin.test)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.logback)
            }
        }

        val posixMain by creating {
            dependsOn(commonMain)
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.okio.nodefilesystem)
            }
        }

        arrayOf("macosX64", "linuxX64").forEach { targetName ->
            findByName("${targetName}Main")?.dependsOn(posixMain)
        }
        tasks.withType<JavaExec> {
            // code to make run task in kotlin multiplatform work
            val compilation = jvmTarget.compilations.getByName<KotlinJvmCompilation>("main")

            val classes =
                files(
                    compilation.runtimeDependencyFiles,
                    compilation.output.allOutputs,
                )
            classpath(classes)
        }
        tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
            archiveBaseName.set(project.name)
            archiveClassifier.set("")
            archiveVersion.set("")

            from(jvmTarget.compilations.getByName("main").output)
            configurations =
                listOf(
                    jvmTarget.compilations.getByName("main").compileDependencyFiles,
                    jvmTarget.compilations.getByName("main").runtimeDependencyFiles,
                ).map { it as Configuration }
        }
    }
}
tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("ShadowJar") {
    group = "build"
    archiveClassifier.set("")
    from(sourceSets["jvmMain"].output)
    configurations = listOf(project.configurations.getByName("jvmRuntimeClasspath"))
    manifest {
        attributes["Main-Class"] = "ai.tenum.cli.MainKt"
    }
}
distributions {
    main {
        contents {
            from(tasks.named("ShadowJar")) {
                into("lib")
            }
            from("scripts") {
                into("bin")
            }
        }
    }
}
