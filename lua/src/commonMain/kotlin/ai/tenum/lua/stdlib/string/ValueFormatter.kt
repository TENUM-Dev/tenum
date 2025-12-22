package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaValue

/**
 * Strategy interface for formatting different value types in Lua format strings.
 *
 * Domain: String formatting
 * Responsibility: Format a single value according to a format specifier
 */
interface ValueFormatter {
    /**
     * Format a value according to the given format specifier.
     *
     * @param value The Lua value to format
     * @param spec The format specifier parsed from the format string
     * @return The formatted string representation
     * @throws IllegalArgumentException if the value type is incompatible with this formatter
     */
    fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String

    /**
     * Check if this formatter can handle the given format character.
     *
     * @param formatChar The format character ('d', 's', 'f', etc.)
     * @return true if this formatter handles this format type
     */
    fun handles(formatChar: Char): Boolean
}
