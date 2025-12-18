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
 * Formats values as floating-point numbers (%f, %g, %G formats).
 *
 * Domain: String formatting
 * Responsibility: Handle float format specifiers with precision and %g/%G smart formatting
 */
class FloatFormatter : ValueFormatter {
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

        val precision = spec.precision ?: 6
        return formatFloatWithPrecision(num, precision, spec.alternate)
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
     * Determine if %g/%G should use exponential format.
     * Per C standard: use exponential if exponent < -4 or exponent >= precision
     */
    private fun shouldUseExponentialForG(
        value: Double,
        precision: Int,
    ): Boolean {
        if (value == 0.0 || value.isNaN() || value.isInfinite()) return false

        val absValue = abs(value)
        val exponent = floor(log10(absValue)).toInt()

        return exponent < -4 || exponent >= precision
    }

    /**
     * Format a number in exponential notation (e.g., 1.234567e+02).
     */
    private fun formatExponential(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String {
        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) return if (uppercase) (if (value > 0) "INF" else "-INF") else (if (value > 0) "inf" else "-inf")

        val expChar = if (uppercase) 'E' else 'e'
        if (value == 0.0) {
            val prec = if (precision >= 0) precision else 6
            return if (prec > 0) "0.${'0'.toString().repeat(prec)}$expChar+00" else "0$expChar+00"
        }

        // Get sign and absolute value
        val isNegative = value < 0
        val absValue = abs(value)

        // Calculate exponent (base 10)
        val exponent = floor(log10(absValue)).toInt()

        // Calculate mantissa using power of 10
        val powerOf10 =
            if (exponent >= 0) {
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
        val prec = if (precision >= 0) precision else 6
        val mantissaStr =
            if (prec == 0) {
                mantissa.toInt().toString()
            } else {
                formatFloatWithPrecision(mantissa, prec, false).trimEnd('0').trimEnd('.')
            }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expSign = if (exponent >= 0) "+" else "-"
        val expStr = abs(exponent).toString().padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
    }

    /**
     * Format float with precision, always using '.' as decimal separator.
     * Uses BigDecimal to handle very large numbers and high precision formatting.
     */
    private fun formatFloatWithPrecision(
        value: Double,
        precision: Int,
        alternateForm: Boolean = false,
    ): String {
        // Use BigDecimal for proper handling of large numbers and high precision
        val rounded =
            BigDecimal
                .fromDouble(value)
                .roundToDigitPositionAfterDecimalPoint(
                    precision.toLong(),
                    RoundingMode.ROUND_HALF_AWAY_FROM_ZERO,
                ).scale(precision.toLong())

        // Convert to plain string (not scientific notation)
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
