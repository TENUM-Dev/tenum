package ai.tenum.lua.vm.typeops

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaValue

/**
 * Shared value-to-string formatting utilities.
 * Centralizes the logic for converting Lua values to their string representations,
 * particularly handling numeric formatting with proper integer/float distinction.
 */
object ValueFormatter {
    /**
     * Converts a Lua value to its string representation following Lua 5.4 semantics.
     * - Numbers are formatted as integers when exactly representable
     * - Doubles only have full integer precision up to 2^53
     */
    fun toString(value: LuaValue<*>): String =
        when (value) {
            is LuaNil -> "nil"
            is LuaBoolean -> value.value.toString()
            is LuaLong -> value.value.toString()
            is LuaDouble -> formatDouble(value.value)
            is LuaString -> value.value
            is LuaTable -> "table: ${value.hashCode()}"
            else -> value.toString()
        }

    /**
     * Formats a double value, using integer format when exactly representable.
     */
    private fun formatDouble(num: Double): String {
        // Only format as integer if it's exactly representable
        // Doubles have full integer precision only up to 2^53
        val maxPreciseInteger = 9007199254740992.0 // 2^53
        return if (num >= -maxPreciseInteger && num <= maxPreciseInteger) {
            val asLong = num.toLong()
            if (asLong.toDouble() == num) {
                asLong.toString()
            } else {
                num.toString()
            }
        } else {
            // Beyond double precision bounds, use float format
            num.toString()
        }
    }
}
