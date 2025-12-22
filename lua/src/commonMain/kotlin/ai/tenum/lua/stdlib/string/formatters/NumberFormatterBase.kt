package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

/**
 * Base class for numeric formatters providing common formatting utilities.
 */
abstract class NumberFormatterBase : ValueFormatter {
    /**
     * Apply sign flags (+ or space) to formatted number.
     */
    protected open fun applySign(
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
    protected open fun applyWidth(
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
    protected open fun formatFloatWithPrecision(
        value: Double,
        precision: Int,
        alternateForm: Boolean = false,
    ): String {
        // Use BigDecimal for proper handling of large numbers and high precision
        // Use scale to keep trailing zeros for the requested precision
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

    /**
     * Determine if %g/%G should use exponential format.
     * Per C standard: use exponential if exponent < -4 or exponent >= precision
     */
    protected open fun shouldUseExponentialForG(
        value: Double,
        precision: Int,
    ): Boolean {
        if (value == 0.0 || value.isNaN() || value.isInfinite()) return false

        val absValue = abs(value)
        val exponent = floor(log10(absValue)).toInt()

        return exponent < -4 || exponent >= precision
    }

    /**
     * Core exponential formatting logic shared by both %e and %g formats.
     */
    protected open fun formatExponentialCore(
        value: Double,
        precision: Int,
        uppercase: Boolean,
        includeZeroPrecision: Boolean,
    ): String {
        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) {
            return if (uppercase) {
                (if (value > 0) "INF" else "-INF")
            } else {
                (if (value > 0) "inf" else "-inf")
            }
        }

        val expChar = if (uppercase) 'E' else 'e'
        if (value == 0.0) {
            val mantissaStr =
                if (precision == 0 && !includeZeroPrecision) {
                    "0"
                } else {
                    "0." + "0".repeat(precision)
                }
            return "$mantissaStr$expChar+00"
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
                if (includeZeroPrecision) {
                    formatFloatWithPrecision(mantissa, prec, true)
                } else {
                    formatFloatWithPrecision(mantissa, prec, false)
                }
            } else {
                formatFloatWithPrecision(mantissa, precision, false)
            }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expSign = if (exponent >= 0) "+" else "-"
        val expStr = abs(exponent).toString().padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
    }
}
