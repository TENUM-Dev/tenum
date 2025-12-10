package ai.tenum.lua.stdlib

actual fun triggerGC() {
    // Try to trigger GC if running in Node.js
    try {
        if (js("typeof global !== 'undefined' && global.gc") as Boolean) {
            js("global.gc()")
        }
    } catch (e: dynamic) {
        // GC not available (browser or Node.js without --expose-gc flag)
        // This is a no-op
    }
}

actual fun getMemoryUsageKB(): Double {
    // Try to get memory usage in Node.js
    return try {
        if (js("typeof process !== 'undefined' && process.memoryUsage") as Boolean) {
            val memUsage = js("process.memoryUsage()")
            val heapUsed = js("memUsage.heapUsed") as Double
            heapUsed / 1024.0
        } else {
            // In browser, estimate based on performance API if available
            if (js("typeof performance !== 'undefined' && performance.memory") as Boolean) {
                val usedBytes = js("performance.memory.usedJSHeapSize") as Double
                usedBytes / 1024.0
            } else {
                // No memory info available
                0.0
            }
        }
    } catch (e: dynamic) {
        0.0
    }
}

actual fun stopGC() {
    // JavaScript doesn't support stopping GC
    // This is a no-op
}

actual fun restartGC() {
    // JavaScript doesn't support explicit GC restart
    // This is a no-op
}
