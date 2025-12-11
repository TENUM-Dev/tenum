import java.util.concurrent.ConcurrentHashMap

plugins {
    id("tenum.conventions.mpplib")
}


group = "ai.tenum"


kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":lua"))
                implementation(libs.okio)
                implementation(libs.okio.fakefilesystem)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.bundles.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // If you use JUnit 5, keep this
    useJUnitPlatform()

    testLogging {
        // Always show which tests passed/failed
        events("skipped", "failed")

        // Full exception stack traces for failures
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL

        // Only show stdout/stderr for failed tests
        showStandardStreams = false
    }
    // Buffer stdout/stderr per test
    val buffers = ConcurrentHashMap<String, StringBuilder>()

    fun key(d: TestDescriptor) = "${d.className}#${d.name}"

    addTestOutputListener(
        object : TestOutputListener {
            override fun onOutput(
                descriptor: TestDescriptor,
                event: TestOutputEvent,
            ) {
                buffers
                    .computeIfAbsent(key(descriptor)) { StringBuilder() }
                    .append(event.message)
            }
        },
    )

    addTestListener(
        object : TestListener {
            override fun afterTest(
                descriptor: TestDescriptor,
                result: TestResult,
            ) {
                if (result.resultType == TestResult.ResultType.FAILURE) {
                    val k = key(descriptor)
                    val out = buffers[k]?.toString()?.trim()
                    if (!out.isNullOrEmpty()) {
                        println("\n--- Captured output for FAILED test: $k ---\n$out\n")
                    }
                }
                // free memory
                buffers.remove(key(descriptor))
            }

            override fun beforeTest(descriptor: TestDescriptor) {}

            override fun beforeSuite(suite: TestDescriptor) {}

            override fun afterSuite(
                suite: TestDescriptor,
                result: TestResult,
            ) {}
        },
    )
}
