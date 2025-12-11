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