package ai.tenum.lua.vm.execution

import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaValue

/**
 * Wrapper for register array that allows dynamic growth while maintaining stable reference.
 * Upvalues can store a reference to this wrapper, and when the array grows,
 * the wrapper updates its internal array without changing the wrapper reference.
 */
class RegisterArray(
    initialSize: Int,
) {
    private var array: Array<LuaValue<*>> = Array(initialSize) { LuaNil }

    val size: Int get() = array.size

    operator fun get(index: Int): LuaValue<*> = array[index]

    operator fun set(
        index: Int,
        value: LuaValue<*>,
    ) {
        array[index] = value
    }

    /**
     * Ensure capacity for the given index, growing if necessary
     */
    fun ensureCapacity(index: Int) {
        if (index >= array.size) {
            val newSize = (index + 1).coerceAtLeast(array.size * 2)
            val newArray =
                Array<LuaValue<*>>(newSize) { i ->
                    if (i < array.size) array[i] else LuaNil
                }
            array = newArray
        }
    }

    /**
     * Get the underlying array (for compatibility)
     */
    fun toArray(): Array<LuaValue<*>> = array
}
