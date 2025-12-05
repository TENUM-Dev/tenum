plugins {
    id("tenum.conventions.common")
    // id("io.github.turansky.kfc.definitions")
    id("org.danilopianini.npm.publish")
}

val ideaActive = System.getProperty("idea.active") == "true"
val compileNative = findProperty("compileNative") == "true"
val useMochaInBrowser = findProperty("useMochaInBrowser") == "true"
val browserHeadless = findProperty("browser.headless") == "true"
val browserType = findProperty("browser.type") ?: "Chrome"
val env = System.getenv()
val isCiServer = env.containsKey("CI")

kotlin {
    js(IR) {
        generateTypeScriptDefinitions()
        binaries.library()
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
        useCommonJs()
    }
    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
            languageSettings.optIn("kotlin.RequiresOptIn")
        }
    }
}

npmPublish {
    val os =
        org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
            .getCurrentOperatingSystem()
    if (os.isWindows) {
        val nodePath = System.getenv("NODE_HOME")

        if (nodePath != null) {
            nodeHome = project.objects.directoryProperty().fileValue(File(nodePath))
            nodeBin = nodeHome.file("node.exe")
            npmBin = nodeHome.file("npm.cmd")
        }
    }
}
