package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier

/**
 * Formats values as characters (%c format).
 *
 * Domain: String formatting
 * Responsibility: Convert numeric values to their character representation
 */
class CharacterFormatter : StringFormatterBase() {
    override fun handles(formatChar: Char): Boolean = formatChar == 'c'

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.value?.toInt() ?: 0
        val char = num.toChar().toString()
        return applyWidth(char, spec)
    }
}
