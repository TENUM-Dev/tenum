package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaValue
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.round

/**
 * String formatting operations for Lua string.format
 * Domain: Format string parsing and value conversion
 */
object StringFormatting {
    /**
     * Format a string with Lua-style placeholders
     * @param formatString the format string
     * @param values the values to format
     * @param valueToString function to convert a value to a string (handles metamethods)
     */
    fun format(
        formatString: String,
        values: List<LuaValue<*>>,
        valueToString: (LuaValue<*>) -> String,
    ): String {
        val registry = FormatterRegistry(valueToString)
        var result = formatString
        var valueIndex = 0

        var i = 0
        while (i < result.length) {
            if (result[i] == '%' && i + 1 < result.length) {
                // Check for optional flags and width specifier
                var j = i + 1

                // Parse flags: -, 0, +, #, space
                var leftAlign = false
                var zeroPad = false
                var forceSign = false
                var spaceSign = false
                var alternateForm = false

                // Parse flags: -, 0 (only once), +, #, space
                // Note: '0' flag can only appear once at the start
                while (j < result.length) {
                    when (result[j]) {
                        '-' -> {
                            leftAlign = true
                            j++
                        }
                        '0' -> {
                            if (j == i + 1 || (j > i + 1 && result[j - 1] !in '0'..'9')) {
                                // Only treat '0' as a flag if it's the first char after % or after other flags
                                zeroPad = true
                                j++
                            } else {
                                // This '0' is part of the width, stop flag parsing
                                break
                            }
                        }
                        '+' -> {
                            forceSign = true
                            j++
                        }
                        ' ' -> {
                            spaceSign = true
                            j++
                        }
                        '#' -> {
                            alternateForm = true
                            j++
                        }
                        else -> break
                    }
                }

                // Parse width
                val widthStart = j
                while (j < result.length && result[j].isDigit()) {
                    j++
                    if ((j - widthStart) > 99) {
                        throw RuntimeException("too long")
                    }
                }
                val widthStr = if (j > widthStart) result.substring(widthStart, j) else ""
                val widthInt = widthStr.toIntOrNull()

                // Check if width string is too long (causes overflow or parsing failure)
                // If the string is very long AND doesn't parse to a valid int, it's "too long"
                // If it parses but is > 99, it's "invalid conversion"
                if (widthStr.length > 99) {
                    throw RuntimeException("too long")
                }

                val width = widthInt ?: 0

                // Parse precision (optional: .digits)
                var precision = -1 // -1 means no precision specified
                if (j < result.length && result[j] == '.') {
                    j++ // Skip the dot
                    val precisionStart = j
                    while (j < result.length && result[j].isDigit()) {
                        j++
                    }
                    val precisionStr = if (j > precisionStart) result.substring(precisionStart, j) else ""
                    val precisionInt = precisionStr.toIntOrNull()

                    // Check if precision string is too long
                    if (precisionStr.length > 99) {
                        throw RuntimeException("too long")
                    }

                    precision = precisionInt ?: 0
                }

                // Validate width and precision (Lua limits them to 99)
                // This matches Lua 5.4 behavior: "invalid format (width or precision too long)"
                // Test files expect "invalid conversion" in the error message
                if (width > 99 || precision > 99) {
                    throw RuntimeException("invalid conversion (width or precision too long)")
                }

                // Now j points to the actual format character
                if (j >= result.length) {
                    i++
                    continue
                }

                val formatChar = result[j]

                // Validate format-specific modifier restrictions
                when (formatChar) {
                    'c' -> {
                        // %c: only accepts width and '-' flag
                        // No zero padding, no precision, no +/space/#
                        val hasInvalidModifiers = zeroPad || precision >= 0 || forceSign || spaceSign || alternateForm
                        if (hasInvalidModifiers) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                    's' -> {
                        // %s: accepts width, '-', and precision
                        // No zero padding, no +/space/#
                        val hasInvalidModifiers = zeroPad || forceSign || spaceSign || alternateForm
                        if (hasInvalidModifiers) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                    'q' -> {
                        // %q: no modifiers at all
                        val hasAnyModifiers =
                            width > 0 ||
                                precision >= 0 ||
                                leftAlign ||
                                zeroPad ||
                                forceSign ||
                                spaceSign ||
                                alternateForm
                        if (hasAnyModifiers) {
                            throw RuntimeException("cannot have modifiers")
                        }
                    }
                    'p' -> {
                        // %p: accepts width and '-' flag
                        // No zero padding, no precision, no +/space/#
                        val hasInvalidModifiers = zeroPad || precision >= 0 || forceSign || spaceSign || alternateForm
                        if (hasInvalidModifiers) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                    'd', 'i', 'u' -> {
                        // %d/%i/%u: no '#' flag (alternate form)
                        if (alternateForm) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                }

                when (formatChar) {
                    '%' -> {
                        // Escape %% -> %
                        val formatReplacement = handleEscapePercent()
                        result = result.substring(0, i) + formatReplacement.replacement + result.substring(j + 1)
                        i += formatReplacement.offset
                        continue
                    }
                    's', 'q', 'd', 'i', 'u', 'o', 'x', 'X', 'f', 'g', 'G', 'e', 'E', 'c', 'p', 'a', 'A' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val spec =
                            FormatSpecifier(
                                flags =
                                    buildSet {
                                        if (leftAlign) add('-')
                                        if (zeroPad) add('0')
                                        if (forceSign) add('+')
                                        if (spaceSign) add(' ')
                                        if (alternateForm) add('#')
                                    },
                                width = if (width > 0) width else null,
                                precision = if (precision >= 0) precision else null,
                                formatChar = formatChar,
                            )
                        val replacement =
                            registry.format(formatChar, values[valueIndex], spec)
                                ?: throw RuntimeException("invalid conversion")
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    else -> {
                        // Invalid format character
                        throw RuntimeException("invalid conversion")
                    }
                }
            } else {
                i++
            }
        }

        return result
    }

    /**
     * Handle %% escape sequence
     */
    private fun handleEscapePercent(): FormatReplacement = FormatReplacement("%", 1)

    /**
     * Data class for format replacement result
     */
    private data class FormatReplacement(
        val replacement: String,
        val offset: Int,
    )

    // ============================================================================
    // Public Utility Methods (used by other modules)
    // ============================================================================

    /**
     * Format a number using %g style formatting with specified precision.
     * This matches Lua's "%.14g" default number formatting.
     * Used by ArgumentHelpers and ValueFormatter.
     *
     * @param value the number to format
     * @param precision number of significant digits (default 14 for Lua)
     * @param lowercase whether to use lowercase 'e' (true) or uppercase 'E' (false)
     * @param alternateForm whether to always include decimal point (# flag)
     * @return formatted string
     */
    fun formatGStyle(
        value: Double,
        precision: Int = 14,
        lowercase: Boolean = true,
        alternateForm: Boolean = false,
    ): String {
        val shouldUseExp = shouldUseExponentialForG(value, precision)

        return if (shouldUseExp) {
            // Use exponential format
            if (!alternateForm) {
                // For %g, trim trailing zeros from mantissa
                formatExponentialForG(value, precision - 1, !lowercase)
            } else {
                // For %#g, keep trailing zeros
                formatExponential(value, precision - 1, !lowercase)
            }
        } else {
            // Use decimal format - need to calculate the right precision
            // For %g, precision means total significant digits, not decimal places
            val absValue = abs(value)
            val exponent =
                if (absValue == 0.0) {
                    0
                } else {
                    floor(log10(absValue)).toInt()
                }
            // Decimal places = precision - (digits before decimal) - 1
            val decimalPlaces = maxOf(0, precision - exponent - 1)
            val raw = formatFloatWithPrecision(value, decimalPlaces, alternateForm)
            if (!alternateForm) raw.trimEnd('0').trimEnd('.') else raw
        }
    }

    /**
     * Determine if %g/%G should use exponential format
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
     * Core exponential formatting logic shared by both %e and %g formats.
     */
    private fun formatExponentialCore(
        value: Double,
        precision: Int,
        uppercase: Boolean,
        trimTrailingZeros: Boolean,
        includeZeroPrecision: Boolean,
    ): String {
        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) return if (uppercase) (if (value > 0) "INF" else "-INF") else (if (value > 0) "inf" else "-inf")

        val expChar = if (uppercase) 'E' else 'e'
        if (value == 0.0) {
            return if (includeZeroPrecision) {
                val prec = if (precision >= 0) precision else 6
                if (prec > 0) "0.${'0'.toString().repeat(prec)}$expChar+00" else "0$expChar+00"
            } else {
                "0$expChar+00"
            }
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
                round(mantissa)
                    .toInt()
                    .toString()
            } else {
                val formatted = formatFloatWithPrecision(mantissa, prec, false)
                if (trimTrailingZeros) formatted.trimEnd('0').trimEnd('.') else formatted
            }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expSign = if (exponent >= 0) "+" else ""
        val expStr =
            abs(exponent)
                .toString()
                .padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
    }

    /**
     * Format a number in exponential notation (e.g., 1.234567e+02)
     */
    private fun formatExponential(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String =
        formatExponentialCore(
            value = value,
            precision = precision,
            uppercase = uppercase,
            trimTrailingZeros = false,
            includeZeroPrecision = true,
        )

    /**
     * Format exponential for %g style (with trailing zero trimming).
     */
    private fun formatExponentialForG(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String =
        formatExponentialCore(
            value = value,
            precision = precision,
            uppercase = uppercase,
            trimTrailingZeros = true,
            includeZeroPrecision = false,
        )

    /**
     * Format float with precision, always using '.' as decimal separator
     * Uses BigDecimal to handle very large numbers and high precision formatting
     * Internal visibility for testing.
     */
    internal fun formatFloatWithPrecision(
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
}
