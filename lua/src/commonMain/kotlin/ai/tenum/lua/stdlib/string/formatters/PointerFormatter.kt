package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier

/**
 * Formats values as pointers (%p format).
 *
 * Domain: String formatting
 * Responsibility: Format reference addresses for Lua values
 */
class PointerFormatter : StringFormatterBase() {
    override fun handles(formatChar: Char): Boolean = formatChar == 'p'

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val pointerStr = formatPointer(value)
        return applyWidth(pointerStr, spec)
    }

    /**
     * Format pointer according to Lua rules:
     * - Primitives (numbers, booleans, nil) → "(null)"
     * - Reference types (tables, functions, coroutines) → unique address
     */
    private fun formatPointer(value: LuaValue<*>): String =
        when (value) {
            is LuaNumber, is LuaBoolean, is LuaNil -> "(null)"
            else -> {
                // For other reference types, use hashCode as unique identifier
                val address = value.hashCode().toString(16)
                "0x$address"
            }
        }
}
