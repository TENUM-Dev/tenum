package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Formats values as strings (%s format).
 *
 * Domain: String formatting
 * Responsibility: Handle %s format specifier
 */
class StringFormatter(
    private val valueToString: (LuaValue<*>) -> String,
) : ValueFormatter {
    override fun handles(formatChar: Char): Boolean = formatChar == 's'

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        var str = valueToString(value)

        // If width is specified and string contains null bytes, error
        if (spec.width != null && spec.width > 0 && str.contains('\u0000')) {
            throw IllegalArgumentException("string contains zeros")
        }

        // Apply precision: truncate string to at most precision characters
        if (spec.precision != null && spec.precision >= 0 && str.length > spec.precision) {
            str = str.substring(0, spec.precision)
        }

        return applyWidth(str, spec.width ?: 0, spec.leftJustify)
    }

    private fun applyWidth(
        str: String,
        width: Int,
        leftAlign: Boolean,
    ): String {
        if (width <= 0 || str.length >= width) return str

        val padding = " ".repeat(width - str.length)
        return if (leftAlign) str + padding else padding + str
    }
}
