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
     * Formats a double value following Lua 5.4 semantics.
     * Uses integer format for exact integers < 2^53, scientific notation for larger values.
     */
    private fun formatDouble(num: Double): String {
        // Handle special cases
        if (!num.isFinite()) {
            return num.toString()
        }

        // Lua uses scientific notation for numbers with absolute value >= 2^53
        // to indicate potential precision loss
        // For smaller numbers, check if it's an exact integer
        val maxPreciseInteger = 9007199254740992.0 // 2^53
        val absNum = kotlin.math.abs(num)

        // Use scientific notation for |num| >= 2^53 (where precision loss can occur)
        return if (absNum < maxPreciseInteger) {
            // Check if it's an exact integer within safe range
            val asLong = num.toLong()
            if (asLong.toDouble() == num) {
                asLong.toString()
            } else {
                // Not an exact integer - use Lua's %.14g format
                ai.tenum.lua.stdlib.string.StringFormatting.formatGStyle(
                    num,
                    precision = 14,
                    lowercase = true,
                    alternateForm = false,
                )
            }
        } else {
            // Beyond 2^53 - use scientific notation to match Lua 5.4
            ai.tenum.lua.stdlib.string.StringFormatting.formatGStyle(
                num,
                precision = 14,
                lowercase = true,
                alternateForm = false,
            )
        }
    }
}
