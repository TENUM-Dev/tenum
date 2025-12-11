package ai.tenum.cli

import okio.FileSystem

actual fun createFileSystem(): okio.FileSystem {
    return FileSystem.SYSTEM
}