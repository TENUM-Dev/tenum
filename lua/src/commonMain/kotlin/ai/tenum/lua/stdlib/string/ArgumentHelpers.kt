package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.vm.execution.opcodes.MethodCallContext

/**
 * Argument extraction and validation utilities for string library functions.
 */
object ArgumentHelpers {
    /**
     * Get first argument as string, coercing if needed
     */
    fun getFirstArgAsString(args: List<LuaValue<*>>): String {
        val first = if (args.isNotEmpty()) args[0] else null
        return coerceToString(first)
    }

    /**
     * Get argument as string with strict type checking.
     * Only accepts LuaString and LuaNumber (which are coerced to string).
     * Throws error for other types.
     */
    fun getArgAsStringStrict(
        args: List<LuaValue<*>>,
        index: Int,
        functionName: String,
    ): String {
        val value = args.getOrNull(index)
        return when (value) {
            is LuaString -> value.value
            is LuaNumber -> coerceToString(value)
            else -> argError(functionName, index + 1, "string", value, allowSelf = index == 0)
        }
    }

    /**
     * Get argument as string with type checking.
     * Only accepts LuaString and LuaNumber (which are coerced to string).
     * Throws error for other types (boolean, table, nil, etc.).
     */
    fun getArgAsStringChecked(
        args: List<LuaValue<*>>,
        index: Int,
        functionName: String,
    ): String {
        val value = args.getOrNull(index)
        return when (value) {
            is LuaString, is LuaNumber -> coerceToString(value)
            else -> argError(functionName, index + 1, "string", value, allowSelf = true)
        }
    }

    /**
     * Get argument as string, with optional default
     */
    fun getArgAsString(
        args: List<LuaValue<*>>,
        index: Int,
        default: String = "",
    ): String {
        val value = args.getOrNull(index)
        return if (value != null) coerceToString(value) else default
    }

    /**
     * Get argument as integer with strict validation and default value
     */
    fun getArgAsIntStrict(
        args: List<LuaValue<*>>,
        index: Int,
        default: Int,
        functionName: String,
    ): Int {
        val value = args.getOrNull(index)
        return when (value) {
            null, is LuaNil -> default
            is LuaLong -> value.value.toInt()
            is LuaDouble -> {
                // Validate integer representation like bitwise operations
                val doubleValue = value.value
                if (!hasIntegerRepresentation(doubleValue)) {
                    throw RuntimeException("number has no integer representation")
                }
                doubleValue.toInt()
            }
            // String library functions can be called with : syntax, so always allow self
            else -> argError(functionName, index + 1, "number", value, allowSelf = true)
        }
    }

    /**
     * Get argument as integer with optional default
     */
    fun getArgAsInt(
        args: List<LuaValue<*>>,
        index: Int,
        default: Int,
    ): Int {
        val value = args.getOrNull(index)
        return (value as? LuaNumber)?.value?.toInt() ?: default
    }

    /**
     * Get argument as integer or null
     */
    fun getArgAsIntOrNull(
        args: List<LuaValue<*>>,
        index: Int,
    ): Int? {
        val value = args.getOrNull(index)
        return (value as? LuaNumber)?.value?.toInt()
    }

    /**
     * Get argument as boolean (Lua truthiness: only nil and false are falsy)
     */
    fun getArgAsBool(
        args: List<LuaValue<*>>,
        index: Int,
        default: Boolean,
    ): Boolean {
        val value = args.getOrNull(index) ?: return default
        // In Lua, only nil and false are falsy; everything else is truthy
        return when (value) {
            is LuaNil -> false
            is LuaBoolean -> value.value
            else -> true // Numbers, strings, tables, etc. are all truthy
        }
    }

    /**
     * Check if a double value has an integer representation
     */
    fun hasIntegerRepresentation(value: Double): Boolean {
        if (!value.isFinite()) return false
        val asLong = value.toLong()
        return value == asLong.toDouble()
    }

    /**
     * Convert a number to string with proper Lua formatting.
     * Lua formats numbers as integers (without decimal point) when they are exactly
     * representable as integers and within a reasonable display range.
     * For large numbers or floats, Lua's default uses a format similar to "%.14g" in C.
     * 
     * Note: This is a simplified version. Lua's actual formatting is more complex
     * and platform-dependent, using sprintf with LUA_NUMBER_FMT (typically "%.14g").
     */
    fun numberToString(num: Number): String {
        val d = num.toDouble()
        
        // Handle special cases
        if (!d.isFinite()) {
            return d.toString()
        }
        
        // Check if it's a reasonably small integer that can be displayed without loss
        // Lua uses scientific notation for very large numbers.
        // The boundary is around 10^14 (100 trillion). We use 10^16 to be safe and include 2^53
        // First check the magnitude before trying toLong() to avoid rounding issues
        if (d >= -9999999999999999.0 && d <= 9999999999999999.0) {
            // Within reasonable range - check if it's an exact integer
            val asLong = d.toLong()
            if (asLong.toDouble() == d) {
                // Exact round-trip - safe to display as integer
                return asLong.toString()
            }
        }
        
        // Use Kotlin's default double formatting
        // This approximates Lua's "%.14g" format
        return d.toString()
    }

    /**
     * Coerce value to string (Lua allows number-to-string coercion)
     * Simple version without metamethod support.
     */
    fun coerceToString(value: LuaValue<*>?): String =
        when (value) {
            is LuaString -> value.value
            is LuaNumber -> numberToString(value.value)
            is LuaNil, null -> ""
            else -> value.toString()
        }

    /**
     * Throw an argument error with proper Lua error formatting
     */
    fun argError(
        functionName: String,
        argIndex: Int,
        expected: String,
        actual: LuaValue<*>?,
        allowSelf: Boolean,
    ): Nothing {
        val actualType = actual?.type()?.name?.lowercase() ?: "nil"
        val isSelfError = allowSelf && argIndex == 1

        // Check if this was a method call (via : syntax)
        val isMethodCall = MethodCallContext.get()

        // For method calls, adjust index to not count self (args[0])
        // For function calls, use actual index
        val reportedIndex =
            if (!isSelfError && isMethodCall && allowSelf && argIndex > 1) {
                argIndex - 1
            } else {
                argIndex
            }

        val indexText =
            if (isSelfError) {
                "bad self"
            } else {
                "bad argument #$reportedIndex to '$functionName'"
            }
        val message = "$indexText ($expected expected, got $actualType)"
        throw RuntimeException(message)
    }
}
