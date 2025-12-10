package ai.tenum.lua.stdlib

import opensavvy.pedestal.weak.WeakRef

/**
 * Garbage Collection support for Lua
 *
 * Platform-specific GC control via expect/actual pattern
 */

expect fun triggerGC()

/**
 * Get memory usage in kilobytes (approximate)
 */
expect fun getMemoryUsageKB(): Double

/**
 * Stop automatic garbage collection
 */
expect fun stopGC()

/**
 * Restart automatic garbage collection
 */
expect fun restartGC()

/**
 * Create a weak reference using the pedestal.weak library
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun <T> createWeakReference(obj: T): WeakRef<T> = WeakRef(obj)
