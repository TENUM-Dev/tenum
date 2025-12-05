plugins {
    id("tenum.conventions.common")
}

val ideaActive = System.getProperty("idea.active") == "true"
val compileNative = findProperty("compileNative") == "true"
val useMochaInBrowser = findProperty("useMochaInBrowser") == "true"
val enableLinuxArm = findProperty("enableLinuxArm") == "true"
val browserHeadless = findProperty("browser.headless") == "true"
val browserType = findProperty("browser.type") ?: "chrome"
val env = System.getenv()
val isCiServer = env.containsKey("CI")
kotlin {
    jvm { }
    js {
        nodejs { testTask { useMocha { timeout = "60s" } } }
        if (useMochaInBrowser) {
            browser {
                testTask { useMocha { timeout = "60s" } }
            }
        } else {
            browser {
                testTask {
                    useKarma {
                        if (browserHeadless) {
                            when (browserType.toString().lowercase()) {
                                "ie" -> useIe()
                                "firefox" -> useFirefoxHeadless()
                                else ->
                                    if (isCiServer) {
                                        useChromeHeadlessNoSandbox()
                                    } else {
                                        useChromeHeadless()
                                    }
                            }
                        } else {
                            when (browserType.toString().lowercase()) {
                                "ie" -> useIe()
                                "firefox" -> useFirefox()
                                else ->
                                    if (isCiServer) {
                                        useChromeHeadlessNoSandbox()
                                    } else {
                                        useChrome()
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
    if (compileNative) {
        if (ideaActive) {
            val os =
                org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
                    .getCurrentOperatingSystem()
            if (os.isWindows) {
                mingwX64()
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
                macosArm64()
            }
        } else {
            val os =
                org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
                    .getCurrentOperatingSystem()
            if (os.isWindows) {
                mingwX64()
                linuxX64()
                if (enableLinuxArm) {
                    linuxArm64()
                }
            } else if (os.isLinux) {
                mingwX64()
                linuxX64()
                if (enableLinuxArm) {
                    linuxArm64()
                }
            } else if (os.isMacOsX) {
                macosArm64()
                macosX64()
                iosArm64()
                iosX64()
                watchosArm32()
                watchosArm64()
                watchosX64()
                tvosArm64()
                tvosX64()
            }
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}
