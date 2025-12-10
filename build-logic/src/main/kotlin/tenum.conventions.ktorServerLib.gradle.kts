plugins {
    id("tenum.conventions.common")
}

val ideaActive = System.getProperty("idea.active") == "true"
val compileNative = findProperty("compileNative") == "true"
val enableLinuxArm = findProperty("enableLinuxArm") == "true"
kotlin {
    jvm { }

    if (compileNative) {
        val os =
            org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
                .getCurrentOperatingSystem()
        if (os.isWindows) {
            // Windows is not supported yet
            // mingwX64()
            linuxX64()
            if (enableLinuxArm) {
                linuxArm64()
            }
        } else if (os.isLinux) {
            linuxX64()
            if (enableLinuxArm) {
                linuxArm64()
            }
        } else if (os.isMacOsX) {
            macosX64()
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}
