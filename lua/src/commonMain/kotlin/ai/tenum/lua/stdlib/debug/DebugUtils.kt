package ai.tenum.lua.stdlib.debug

internal expect fun formatHex(byte: Byte): String

internal expect fun writeDebugChunk(bytes: ByteArray): String
