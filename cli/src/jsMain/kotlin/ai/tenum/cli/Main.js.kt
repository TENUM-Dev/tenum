package ai.tenum.cli

import okio.FileSystem
import okio.NodeJsFileSystem

actual fun createFileSystem(): FileSystem {
    return NodeJsFileSystem
}