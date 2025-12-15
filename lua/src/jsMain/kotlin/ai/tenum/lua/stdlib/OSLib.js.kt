package ai.tenum.lua.stdlib

// CPD-OFF: platform-specific code
external val process: dynamic

actual fun getPlatform(): String = "JS"

actual fun getOs(): String = js("typeof process !== 'undefined' && process.platform ? process.platform : 'unknown'").toString()

actual fun getPlatformEnvironmentVariable(name: String): String? =
    try {
        val value = js("typeof process !== 'undefined' && process.env ? process.env[name] : undefined")
        if (value == null || js("value === undefined") as Boolean) {
            null
        } else {
            value.toString()
        }
    } catch (e: Exception) {
        null
    }

actual fun executePlatformCommand(command: String): Int {
    return try {
        // Check if we're in Node.js environment
        val hasChildProcess = js("typeof process !== 'undefined' && typeof require !== 'undefined'") as Boolean

        if (!hasChildProcess) {
            // Browser environment - command execution not supported
            return 1
        }

        // Node.js environment - use child_process.execSync
        @Suppress("UNUSED_VARIABLE")
        val cmd = command
        val result =
            js(
                """
            (function() {
                try {
                    var execSync = require('child_process').execSync;
                    execSync(cmd, { stdio: 'inherit' });
                    return 0;
                } catch (error) {
                    return error.status || 1;
                }
            })()
        """,
            )

        (result as? Number)?.toInt() ?: 1
    } catch (e: Exception) {
        1 // Return failure code on error
    }
}

actual fun exitProcess(
    code: Int,
    closeState: Boolean,
): Nothing {
    try {
        js("process.exit(code)")
    } catch (_: dynamic) {
    }
    throw RuntimeException("os.exit called with code $code (process termination not available)")
}
