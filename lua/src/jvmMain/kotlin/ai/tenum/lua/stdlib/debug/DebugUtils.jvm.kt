package ai.tenum.lua.stdlib.debug

import java.io.File

internal actual fun formatHex(byte: Byte): String = String.format("%02X", byte.toInt() and 0xFF)

internal actual fun writeDebugChunk(bytes: ByteArray): String {
    val outFile = File("build/tmp/failed-chunk-${System.currentTimeMillis()}.bin")
    outFile.parentFile.mkdirs()
    outFile.writeBytes(bytes)
    return outFile.path
}
