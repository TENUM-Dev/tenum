package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Formats values as hexadecimal floating-point (%a, %A formats).
 *
 * Domain: String formatting
 * Responsibility: Format floats in hexadecimal notation (e.g., 0x1.91eb851eb851fp+1)
 */
class HexFloatFormatter : ValueFormatter {
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
            return if (precision != null && precision >= 0) {
                // With precision, always show decimal point
                "${sign}0x0." + "0".repeat(precision) + if (uppercase) "P+0" else "p+0"
            } else {
                "${sign}0x0" + if (uppercase) "P+0" else "p+0"
            }
        }

        val bits = value.toRawBits()
        val negative = bits and (1L shl 63) != 0L
        val exponent = ((bits shr 52) and 0x7FF).toInt() - 1023
        val mantissa = bits and 0xFFFFFFFFFFFFFL

        val signStr = if (negative) "-" else ""
        val hexPrefix = if (uppercase) "0X" else "0x"
        val expChar = if (uppercase) 'P' else 'p'

        // The mantissa has 52 bits, which is 13 hex digits
        val mantissaHex = mantissa.toString(16).padStart(13, '0')

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
        if (mantissa == 0L && (precision == null || precision < 0)) {
            return "${signStr}${hexPrefix}1$expChar${if (exponent >= 0) "+" else ""}$exponent"
        }

        // Include decimal point if we have mantissa digits or precision is explicitly set
        val decimalPart =
            if (formattedMantissa.isEmpty() && (precision == null || precision < 0)) {
                ""
            } else {
                ".$formattedMantissa"
            }

        val result = "${signStr}${hexPrefix}1${decimalPart}$expChar${if (exponent >= 0) "+" else ""}$exponent"
        return if (uppercase) result.uppercase() else result
    }

    /**
     * Apply sign flags (+ or space) to formatted number.
     */
    private fun applySign(
        formatted: String,
        num: Double,
        spec: FormatSpecifier,
    ): String {
        var result = formatted

        // Apply sign flags: + takes precedence over space
        if (spec.forceSign && num >= 0.0 && !result.startsWith('+')) {
            result = "+$result"
        } else {
            val shouldAddSpaceSign = !spec.forceSign && spec.spaceForSign && num >= 0.0
            val hasNoSign = !result.startsWith('+') && !result.startsWith(' ')
            if (shouldAddSpaceSign && hasNoSign) {
                result = " $result"
            }
        }

        return applyWidth(result, spec)
    }

    /**
     * Apply width formatting with proper zero-padding handling.
     */
    private fun applyWidth(
        str: String,
        spec: FormatSpecifier,
    ): String {
        val width = spec.width ?: 0
        if (width <= 0 || str.length >= width) return str

        val padChar = if (spec.zeroPad && !spec.leftJustify) '0' else ' '
        val padding = padChar.toString().repeat(width - str.length)

        return if (spec.leftJustify) {
            str + padding
        } else {
            val hasSign = str.startsWith('-') || str.startsWith('+') || str.startsWith(' ')
            if (spec.zeroPad && hasSign) {
                // For numbers with sign, put sign first, then zero padding, then digits
                str[0] + padding + str.substring(1)
            } else {
                padding + str
            }
        }
    }
}
