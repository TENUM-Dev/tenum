package ai.tenum.lua.vm.typeops

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaDouble
import ai.tenum.lua.runtime.LuaLong
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaValue

/**
 * Type conversion operations for Lua values.
 *
 * Handles conversions between Lua types following Lua 5.4 semantics.
 */
internal class TypeConversions {
    /**
     * Converts a Lua value to a number (double).
     *
     * - LuaLong/LuaDouble: converts to double
     * - LuaString: attempts string-to-number conversion, returns 0.0 on failure
     * - Other types: returns 0.0
     */
    fun toNumber(value: LuaValue<*>): Double =
        when (value) {
            is LuaLong -> value.value.toDouble()
            is LuaDouble -> value.value
            is LuaString -> value.value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    /**
     * Converts a Lua value to a string representation.
     *
     * Follows Lua 5.4 formatting rules:
     * - nil: "nil"
     * - boolean: "true" or "false"
     * - number: integer format if whole number, otherwise decimal
     * - string: the string value itself
     * - table: "table: <hashcode>"
     * - other: default toString()
     */
    fun toString(value: LuaValue<*>): String = ValueFormatter.toString(value)

    /**
     * Evaluates the "truthiness" of a Lua value.
     *
     * In Lua:
     * - nil and false are falsy
     * - Everything else (including 0, empty strings, empty tables) is truthy
     */
    fun isTruthy(value: LuaValue<*>): Boolean =
        when (value) {
            is LuaNil -> false
            is LuaBoolean -> value.value
            else -> true
        }
}
