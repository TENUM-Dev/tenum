plugins {
    id("tenum.conventions.mpplib")
    alias(libs.plugins.benchmark)
    alias(libs.plugins.allopen)
}


group = "ai.tenum"

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvm {
    }

    applyDefaultHierarchyTemplate()

    sourceSets.configureEach {
        languageSettings {
            progressiveMode = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":lua"))
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.bundles.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// Configure benchmark
benchmark {
    targets {
        register("jvm")
    }
}