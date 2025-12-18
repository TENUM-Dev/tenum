package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * Formats values as floating-point numbers (%f, %g, %G formats).
 *
 * Domain: String formatting
 * Responsibility: Handle float format specifiers with precision and %g/%G smart formatting
 */
class FloatFormatter : NumberFormatterBase() {
    override fun handles(formatChar: Char): Boolean = formatChar in setOf('f', 'g', 'G')

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0

        val formatted =
            when (spec.formatChar) {
                'f' -> formatFixed(num, spec)
                'g', 'G' -> formatGeneral(num, spec)
                else -> throw IllegalArgumentException("Unsupported format: ${spec.formatChar}")
            }

        return applySign(formatted, num, spec)
    }

    /**
     * Format with fixed decimal precision (%f).
     */
    private fun formatFixed(
        num: Double,
        spec: FormatSpecifier,
    ): String {
        // Handle special values early
        if (num.isNaN()) return "nan"
        if (num.isInfinite()) return if (num > 0) "inf" else "-inf"

        // When precision is not specified, use default toString()
        if (spec.precision == null) {
            return num.toString()
        }

        return formatFloatWithPrecision(num, spec.precision, spec.alternate)
    }

    /**
     * Format with general formatting (%g, %G).
     * Chooses between exponential and fixed based on magnitude.
     */
    private fun formatGeneral(
        num: Double,
        spec: FormatSpecifier,
    ): String {
        // Handle special values early
        if (num.isNaN()) return if (spec.formatChar == 'G') "NAN" else "nan"
        if (num.isInfinite()) {
            val infStr = if (num > 0) "INF" else "-INF"
            return if (spec.formatChar == 'G') infStr else infStr.lowercase()
        }

        // Default precision for %g is 6 if not specified
        val precision = spec.precision ?: 6
        val shouldUseExp = shouldUseExponentialForG(num, precision)

        return if (shouldUseExp) {
            // Use exponential format
            val exp = formatExponential(num, precision - 1, spec.formatChar == 'G')
            if (!spec.alternate) exp.trimEnd('0').trimEnd('.') else exp
        } else {
            // Use decimal format - precision means total significant digits
            val absValue = abs(num)
            val exponent = if (absValue == 0.0) 0 else floor(log10(absValue)).toInt()
            val decimalPlaces = maxOf(0, precision - exponent - 1)
            val raw = formatFloatWithPrecision(num, decimalPlaces, spec.alternate)
            if (!spec.alternate) raw.trimEnd('0').trimEnd('.') else raw
        }
    }

    /**
     * Format a number in exponential notation for %g format.
     */
    private fun formatExponential(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String {
        val formatted = formatExponentialCore(value, precision, uppercase, false)
        // For %g, trim trailing zeros
        return formatted
            .replace(Regex("""(\.\d*?)0+([eE])"""), "$1$2")
            .replace(Regex("""\.([eE])"""), "$1")
    }
}
