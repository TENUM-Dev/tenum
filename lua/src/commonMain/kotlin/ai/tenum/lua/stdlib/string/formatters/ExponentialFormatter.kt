package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier

/**
 * Formats values in exponential notation (%e, %E formats).
 *
 * Domain: String formatting
 * Responsibility: Handle exponential format specifiers (e.g., 1.234567e+02)
 */
class ExponentialFormatter : NumberFormatterBase() {
    override fun handles(formatChar: Char): Boolean = formatChar in setOf('e', 'E')

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0
        val formatted = formatExponential(num, spec)
        return applySign(formatted, num, spec)
    }

    /**
     * Format a number in exponential notation (e.g., 1.234567e+02).
     */
    private fun formatExponential(
        num: Double,
        spec: FormatSpecifier,
    ): String {
        val uppercase = spec.formatChar == 'E'
        val precision = spec.precision ?: 6

        // Use the base class formatExponentialCore for the core logic
        return formatExponentialCore(num, precision, uppercase, spec.alternate)
    }
}
