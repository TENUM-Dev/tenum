package ai.tenum.lua.stdlib

import kotlin.native.runtime.NativeRuntimeApi
import kotlin.native.runtime.GC as NativeGC

@OptIn(NativeRuntimeApi::class)
actual fun triggerGC() {
    // Trigger Kotlin/Native garbage collection
    NativeGC.collect()
}

actual fun getMemoryUsageKB(): Double {
    // Kotlin/Native doesn't expose detailed heap memory metrics
    // Return 0 as we can't reliably measure memory usage
    return 0.0
}

actual fun stopGC() {
    // Kotlin/Native doesn't support stopping GC
    // This is a no-op
}

actual fun restartGC() {
    // Kotlin/Native doesn't support explicit GC restart
    // This is a no-op
}
