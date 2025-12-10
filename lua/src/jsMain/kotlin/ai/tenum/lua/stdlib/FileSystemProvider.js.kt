package ai.tenum.lua.stdlib

import okio.FileSystem
import okio.fakefilesystem.FakeFileSystem

/**
 * Get the appropriate FileSystem for JavaScript platform
 * - Node.js: Use NodeJsFileSystem for real filesystem access
 * - Browser: Use FakeFileSystem (in-memory) as browsers don't have file system access
 */
actual fun getFileSystem(): FileSystem = fileSystem.value

private val fileSystem =
    lazy {
        if (isBrowser()) {
            FakeFileSystem()
        } else {
            FakeFileSystem()
        }
    }

/**
 * Detect if we're running in Node.js vs browser
 */
private fun isNodeJs(): Boolean =
    try {
        js("typeof process !== 'undefined' && process.versions != null && process.versions.node != null") as Boolean
    } catch (e: dynamic) {
        false
    }

private fun isBrowser(): Boolean = !isNodeJs()
