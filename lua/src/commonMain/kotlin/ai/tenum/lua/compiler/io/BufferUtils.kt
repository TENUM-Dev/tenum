package ai.tenum.lua.compiler.io

import okio.BufferedSink
import okio.BufferedSource

/**
 * Buffer utilities for reading/writing size_t, endianness, etc.
 */
object BufferUtils {
    fun BufferedSink.writeSizeT(value: Long) {
        // Always write as 8 bytes little-endian (Lua 5.4 default)
        this.writeLongLe(value)
    }

    fun BufferedSource.readSizeT(): Long {
        // Always read as 8 bytes little-endian
        return this.readLongLe()
    }
}
