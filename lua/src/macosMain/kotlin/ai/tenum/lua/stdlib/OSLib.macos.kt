@file:OptIn(ExperimentalForeignApi::class)

package ai.tenum.lua.stdlib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.exit
import platform.posix.getenv
import platform.posix.system

actual fun getPlatform(): String = "native"

actual fun getOs(): String = "macos"

actual fun getPlatformEnvironmentVariable(name: String): String? = getenv(name)?.toKString()

actual fun executePlatformCommand(command: String): Int = system(command)

actual fun exitProcess(
    code: Int,
    closeState: Boolean,
): Nothing {
    // closeState parameter is ignored - exit always terminates the process
    exit(code)
    throw RuntimeException("Unreachable") // Needed for Nothing return type
}

