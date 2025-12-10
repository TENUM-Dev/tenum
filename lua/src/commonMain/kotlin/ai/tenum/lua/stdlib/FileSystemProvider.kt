package ai.tenum.lua.stdlib

import okio.FileSystem

/**
 * Get the platform-specific default FileSystem
 *
 * - JVM/Native: FileSystem.SYSTEM (POSIX-compliant)
 * - JS: NodeJsFileSystem (Node.js specific implementation)
 */
expect fun getFileSystem(): FileSystem
