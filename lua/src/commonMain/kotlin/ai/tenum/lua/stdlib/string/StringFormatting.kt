package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.formatters.NumberFormatterBase
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10

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
        val shouldUseExp = formatterHelper.shouldUseExponentialForG(value, precision)

        return if (shouldUseExp) {
            // Use exponential format
            val formatted = formatterHelper.formatExponentialCore(value, precision - 1, !lowercase, !alternateForm)
            if (!alternateForm) {
                // For %g, trim trailing zeros from mantissa
                formatted
                    .replace(Regex("""(\.\d*?)0+([eE])"""), "$1$2")
                    .replace(Regex("""\.([eE])"""), "$1")
            } else {
                formatted
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

    // Helper class for accessing NumberFormatterBase utilities
    private val formatterHelper = FormatterHelper()

    private class FormatterHelper : NumberFormatterBase() {
        override fun handles(formatChar: Char) = false

        override fun format(
            value: LuaValue<*>,
            spec: FormatSpecifier,
        ) = ""

        // Expose protected methods as public
        public override fun shouldUseExponentialForG(
            value: Double,
            precision: Int,
        ) = super.shouldUseExponentialForG(value, precision)

        public override fun formatExponentialCore(
            value: Double,
            precision: Int,
            uppercase: Boolean,
            includeZeroPrecision: Boolean,
        ) = super.formatExponentialCore(value, precision, uppercase, includeZeroPrecision)

        public override fun formatFloatWithPrecision(
            value: Double,
            precision: Int,
            alternateForm: Boolean,
        ) = super.formatFloatWithPrecision(value, precision, alternateForm)
    }

    /**
     * Format float with precision, always using '.' as decimal separator.
     * Uses BigDecimal to handle very large numbers and high precision formatting.
     * Internal visibility for testing.
     */
    internal fun formatFloatWithPrecision(
        value: Double,
        precision: Int,
        alternateForm: Boolean = false,
    ): String = formatterHelper.formatFloatWithPrecision(value, precision, alternateForm)
}
