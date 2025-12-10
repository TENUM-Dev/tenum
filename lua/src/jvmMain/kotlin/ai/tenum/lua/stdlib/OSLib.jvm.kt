package ai.tenum.lua.stdlib

actual fun getPlatform(): String = "jvm"

actual fun getOs(): String = System.getProperty("os.name") ?: "unknown"

actual fun getPlatformEnvironmentVariable(name: String): String? =
    try {
        System.getenv(name)
    } catch (e: Exception) {
        null
    }

actual fun executePlatformCommand(command: String): Int =
    try {
        val osName = System.getProperty("os.name").lowercase()
        val process =
            when {
                osName.contains("win") -> {
                    // Windows: use cmd.exe /c
                    Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", command))
                }
                else -> {
                    // Unix-like: use sh -c
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                }
            }

        // Wait for process to complete and return exit code
        process.waitFor()
    } catch (e: Exception) {
        1 // Return failure code on error
    }
