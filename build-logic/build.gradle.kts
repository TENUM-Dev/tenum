plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "tenum.conventions"

repositories {
    mavenCentral()
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    implementation("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    implementation(libs.kotlinx.serialization.json)
    //implementation("org.zkovari.changelog:org.zkovari.changelog.gradle.plugin:0.4.0")
    implementation("com.diffplug.spotless-changelog:com.diffplug.spotless-changelog.gradle.plugin:3.1.2")
    implementation("org.nosphere.gradle.github.actions:org.nosphere.gradle.github.actions.gradle.plugin:1.4.0")
    implementation("com.palantir.gradle.docker:gradle-docker:0.37.0")
    implementation("org.danilopianini.npm.publish:org.danilopianini.npm.publish.gradle.plugin:4.1.10")
    testImplementation(libs.bundles.convention.test.dependencies)
}

ktlint {
    filter {
        exclude { entry ->
            entry.file.toString().contains("generated")
        }
    }
}
