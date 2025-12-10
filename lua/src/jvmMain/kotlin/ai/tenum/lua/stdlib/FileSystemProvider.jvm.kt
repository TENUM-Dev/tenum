package ai.tenum.lua.stdlib

import okio.FileSystem

actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM
