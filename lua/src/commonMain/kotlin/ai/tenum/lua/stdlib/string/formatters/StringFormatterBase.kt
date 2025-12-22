package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Base class for string formatters providing basic width formatting.
 */
abstract class StringFormatterBase : ValueFormatter {
    /**
     * Apply width formatting with space padding.
     */
    protected open fun applyWidth(
        str: String,
        spec: FormatSpecifier,
    ): String {
        val width = spec.width ?: 0
        if (width <= 0 || str.length >= width) return str

        val padding = " ".repeat(width - str.length)
        return if (spec.leftJustify) str + padding else padding + str
    }
}
