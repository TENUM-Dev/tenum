package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * Formats values in exponential notation (%e, %E formats).
 *
 * Domain: String formatting
 * Responsibility: Handle exponential format specifiers (e.g., 1.234567e+02)
 */
class ExponentialFormatter : ValueFormatter {
    override fun handles(formatChar: Char): Boolean =
        formatChar in setOf('e', 'E')

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
        // Handle special values
        val uppercase = spec.formatChar == 'E'
        if (num.isNaN()) return if (uppercase) "NAN" else "nan"
        if (num.isInfinite()) {
            return if (uppercase) {
                if (num > 0) "INF" else "-INF"
            } else {
                if (num > 0) "inf" else "-inf"
            }
        }

        val expChar = if (uppercase) 'E' else 'e'
        val precision = spec.precision ?: 6

        if (num == 0.0) {
            return if (precision > 0) {
                "0.${'0'.toString().repeat(precision)}$expChar+00"
            } else {
                "0$expChar+00"
            }
        }

        // Get sign and absolute value
        val isNegative = num < 0
        val absValue = abs(num)

        // Calculate exponent (base 10)
        val exponent = floor(log10(absValue)).toInt()

        // Calculate mantissa using power of 10
        val powerOf10 = if (exponent >= 0) {
            var result = 1.0
            repeat(exponent) { result *= 10.0 }
            result
        } else {
            var result = 1.0
            repeat(-exponent) { result /= 10.0 }
            result
        }
        val mantissa = absValue / powerOf10

        // Format mantissa with precision
        val mantissaStr = if (precision == 0) {
            val intStr = mantissa.toInt().toString()
            // With alternate form, always show decimal point even for precision 0
            if (spec.alternate) "$intStr." else intStr
        } else {
            formatFloatWithPrecision(mantissa, precision, spec.alternate)
        }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expSign = if (exponent >= 0) "+" else "-"
        val expStr = abs(exponent).toString().padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
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

    /**
     * Format float with precision, always using '.' as decimal separator.
     */
    private fun formatFloatWithPrecision(
        value: Double,
        precision: Int,
        alternateForm: Boolean,
    ): String {
        val rounded = BigDecimal.fromDouble(value)
            .roundToDigitPositionAfterDecimalPoint(
                precision.toLong(),
                RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
            ).scale(precision.toLong())

        var result = rounded.toPlainString()

        // Special case: precision 0
        if (precision == 0) {
            val dotIndex = result.indexOf('.')
            if (dotIndex >= 0) {
                result = result.substring(0, dotIndex)
            }
            // With alternateForm flag, always show decimal point even for precision 0
            if (alternateForm) {
                result += "."
            }
            return result
        }

        // Ensure we have the decimal point
        if (!result.contains('.')) {
            result += "."
        }

        // Ensure we have exactly the requested precision
        val dotIndex = result.indexOf('.')
        val currentPrecision = result.length - dotIndex - 1

        when {
            currentPrecision < precision -> result += "0".repeat(precision - currentPrecision)
            currentPrecision > precision -> result = result.substring(0, dotIndex + precision + 1)
        }

        return result
    }
}
