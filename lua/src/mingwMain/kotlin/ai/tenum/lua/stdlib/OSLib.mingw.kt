@file:OptIn(ExperimentalForeignApi::class)

package ai.tenum.lua.stdlib

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import platform.posix.system

actual fun getPlatform(): String = "native"

actual fun getOs(): String = "windows"

actual fun getPlatformEnvironmentVariable(name: String): String? = getenv(name)?.toKString()

actual fun executePlatformCommand(command: String): Int = system(command)
