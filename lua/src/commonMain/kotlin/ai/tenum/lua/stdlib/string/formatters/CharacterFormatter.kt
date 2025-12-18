package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Formats values as characters (%c format).
 *
 * Domain: String formatting
 * Responsibility: Convert numeric values to their character representation
 */
class CharacterFormatter : ValueFormatter {
    override fun handles(formatChar: Char): Boolean = formatChar == 'c'

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.value?.toInt() ?: 0
        val char = num.toChar().toString()
        return applyWidth(char, spec)
    }

    /**
     * Apply width formatting.
     */
    private fun applyWidth(
        str: String,
        spec: FormatSpecifier,
    ): String {
        val width = spec.width ?: 0
        if (width <= 0 || str.length >= width) return str

        val padding = " ".repeat(width - str.length)
        return if (spec.leftJustify) str + padding else padding + str
    }
}
