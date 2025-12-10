package ai.tenum.lua.stdlib

actual fun triggerGC() {
    System.gc()
    // Give GC time to run
    Thread.sleep(10)
}

actual fun getMemoryUsageKB(): Double {
    val runtime = Runtime.getRuntime()
    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
    return usedMemory / 1024.0
}

actual fun stopGC() {
    // JVM doesn't support stopping GC
    // This is a no-op
}

actual fun restartGC() {
    // JVM doesn't support explicit GC restart
    // This is a no-op
}
