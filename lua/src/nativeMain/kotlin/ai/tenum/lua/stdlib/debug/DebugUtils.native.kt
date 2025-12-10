// Native does not have a file system by default in the same way as JVM.
// We can still format the hex string for the error message.
package ai.tenum.lua.stdlib.debug

// CPD-OFF: multiplatform implementation (necessary for Native platform)
internal actual fun formatHex(byte: Byte): String = (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')

internal actual fun writeDebugChunk(bytes: ByteArray): String {
    // No-op on Native
    return "(not saved)"
}
