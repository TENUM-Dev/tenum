package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier

/**
 * Formats values as hexadecimal floating-point (%a, %A formats).
 *
 * Domain: String formatting
 * Responsibility: Format floats in hexadecimal notation (e.g., 0x1.91eb851eb851fp+1)
 */
class HexFloatFormatter : NumberFormatterBase() {
    override fun handles(formatChar: Char): Boolean = formatChar in setOf('a', 'A')

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0
        val formatted = formatHexFloat(num, spec)
        return applySign(formatted, num, spec)
    }

    /**
     * Format a float as hex float (e.g., 0x1.91eb851eb851fp+1 for 3.14).
     */
    private fun formatHexFloat(
        value: Double,
        spec: FormatSpecifier,
    ): String {
        val uppercase = spec.formatChar == 'A'
        val precision = spec.precision

        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) {
            return if (uppercase) {
                if (value > 0) "INF" else "-INF"
            } else {
                if (value > 0) "inf" else "-inf"
            }
        }

        if (value == 0.0) {
            // Special case for zero (handle both +0 and -0)
            val sign = if (1.0 / value < 0) "-" else ""
            val hexPrefix = if (uppercase) "0X" else "0x"
            val expChar = if (uppercase) 'P' else 'p'
            return if (precision != null && precision >= 0) {
                // With precision, always show decimal point
                "${sign}${hexPrefix}0." + "0".repeat(precision) + "$expChar+0"
            } else {
                "${sign}${hexPrefix}0$expChar+0"
            }
        }

        val doubleBits = DoubleUtils.extractBits(value)

        val signStr = if (doubleBits.negative) "-" else ""
        val hexPrefix = if (uppercase) "0X" else "0x"
        val expChar = if (uppercase) 'P' else 'p'

        // The mantissa has 52 bits, which is 13 hex digits
        val mantissaHex = doubleBits.mantissa.toString(16).padStart(13, '0')

        // Apply precision if specified
        val formattedMantissa =
            if (precision != null && precision >= 0) {
                // Precision specifies number of hex digits after decimal point
                mantissaHex.take(precision).padEnd(precision, '0')
            } else {
                // No precision: remove trailing zeros from mantissa
                mantissaHex.trimEnd('0')
            }

        // If mantissa is zero (exact power of 2) and no precision specified
        if (doubleBits.mantissa == 0L && (precision == null || precision < 0)) {
            return "${signStr}${hexPrefix}1$expChar${if (doubleBits.exponent >= 0) "+" else ""}${doubleBits.exponent}"
        }

        // Include decimal point if we have mantissa digits or precision is explicitly set
        val decimalPart =
            if (formattedMantissa.isEmpty() && (precision == null || precision < 0)) {
                ""
            } else {
                ".${if (uppercase) formattedMantissa.uppercase() else formattedMantissa}"
            }

        return "${signStr}${hexPrefix}1${decimalPart}$expChar${if (doubleBits.exponent >= 0) "+" else ""}${doubleBits.exponent}"
    }
}
