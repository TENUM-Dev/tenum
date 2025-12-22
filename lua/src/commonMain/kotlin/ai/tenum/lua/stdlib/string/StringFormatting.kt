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
     * Flags parsed from format specifier.
     */
    private data class ParsedFlags(
        val leftAlign: Boolean,
        val zeroPad: Boolean,
        val forceSign: Boolean,
        val spaceSign: Boolean,
        val alternateForm: Boolean,
    )

    /**
     * Width and precision parsed from format specifier.
     */
    private data class ParsedDimensions(
        val width: Int,
        val precision: Int, // -1 means not specified
    )

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
                // Parse format specifier components
                var j = i + 1

                // Parse flags
                val (flags, afterFlags) = parseFlags(result, j, i)
                j = afterFlags

                // Parse width and precision
                val (dimensions, afterDimensions) = parseDimensions(result, j)
                j = afterDimensions

                // Now j points to the actual format character
                if (j >= result.length) {
                    i++
                    continue
                }

                val formatChar = result[j]

                // Validate format-specific modifier restrictions
                validateModifiers(formatChar, flags, dimensions)

                when (formatChar) {
                    '%' -> {
                        // Escape %% -> %
                        val formatReplacement = handleEscapePercent()
                        result = replaceInResult(result, i, j, formatReplacement.replacement)
                        i += formatReplacement.offset
                        continue
                    }
                    's', 'q', 'd', 'i', 'u', 'o', 'x', 'X', 'f', 'g', 'G', 'e', 'E', 'c', 'p', 'a', 'A' -> {
                        val replacement = processFormatSpecifier(formatChar, flags, dimensions, values, valueIndex, registry)
                        result = replaceInResult(result, i, j, replacement)
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
     * Create FormatSpecifier from parsed flags and dimensions.
     */
    private fun createFormatSpecifier(
        flags: ParsedFlags,
        dimensions: ParsedDimensions,
        formatChar: Char,
    ): FormatSpecifier =
        FormatSpecifier(
            flags =
                buildSet {
                    if (flags.leftAlign) add('-')
                    if (flags.zeroPad) add('0')
                    if (flags.forceSign) add('+')
                    if (flags.spaceSign) add(' ')
                    if (flags.alternateForm) add('#')
                },
            width = if (dimensions.width > 0) dimensions.width else null,
            precision = if (dimensions.precision >= 0) dimensions.precision else null,
            formatChar = formatChar,
        )

    /**
     * Process a format specifier and return the replacement string.
     */
    private fun processFormatSpecifier(
        formatChar: Char,
        flags: ParsedFlags,
        dimensions: ParsedDimensions,
        values: List<LuaValue<*>>,
        valueIndex: Int,
        registry: FormatterRegistry,
    ): String {
        if (valueIndex >= values.size) {
            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
        }
        val spec = createFormatSpecifier(flags, dimensions, formatChar)
        return registry.format(formatChar, values[valueIndex], spec)
            ?: throw RuntimeException("invalid conversion")
    }

    /**
     * Replace substring in result with replacement text.
     */
    private fun replaceInResult(
        result: String,
        startPos: Int,
        endPos: Int,
        replacement: String,
    ): String = result.substring(0, startPos) + replacement + result.substring(endPos + 1)

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

    /**
     * Parse flags from format string starting at position j.
     * Returns the parsed flags and the new position after flags.
     */
    private fun parseFlags(
        result: String,
        startPos: Int,
        percentPos: Int,
    ): Pair<ParsedFlags, Int> {
        var j = startPos
        var leftAlign = false
        var zeroPad = false
        var forceSign = false
        var spaceSign = false
        var alternateForm = false

        while (j < result.length) {
            when (result[j]) {
                '-' -> {
                    leftAlign = true
                    j++
                }
                '0' -> {
                    if (j == percentPos + 1 || (j > percentPos + 1 && result[j - 1] !in '0'..'9')) {
                        zeroPad = true
                        j++
                    } else {
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

        return Pair(
            ParsedFlags(leftAlign, zeroPad, forceSign, spaceSign, alternateForm),
            j,
        )
    }

    /**
     * Parse a single dimension (width or precision) from format string.
     * Returns the parsed value and the new position.
     */
    private fun parseSingleDimension(
        result: String,
        startPos: Int,
    ): Pair<Int, Int> {
        var j = startPos
        val dimensionStart = j

        while (j < result.length && result[j].isDigit()) {
            j++
            if ((j - dimensionStart) > 99) {
                throw RuntimeException("too long")
            }
        }

        val dimensionStr = if (j > dimensionStart) result.substring(dimensionStart, j) else ""

        if (dimensionStr.length > 99) {
            throw RuntimeException("too long")
        }

        val value = dimensionStr.toIntOrNull() ?: 0

        if (value > 99) {
            throw RuntimeException("invalid conversion (width or precision too long)")
        }

        return Pair(value, j)
    }

    /**
     * Parse width and precision from format string starting at position j.
     * Returns the parsed dimensions and the new position after dimensions.
     */
    private fun parseDimensions(
        result: String,
        startPos: Int,
    ): Pair<ParsedDimensions, Int> {
        // Parse width
        val (width, afterWidth) = parseSingleDimension(result, startPos)
        var j = afterWidth

        // Parse precision (optional: .digits)
        val precision =
            if (j < result.length && result[j] == '.') {
                j++ // Skip dot
                val (prec, afterPrecision) = parseSingleDimension(result, j)
                j = afterPrecision
                prec
            } else {
                -1
            }

        return Pair(ParsedDimensions(width, precision), j)
    }

    /**
     * Check if any sign-related flags are set.
     */
    private fun hasSignFlags(flags: ParsedFlags): Boolean = flags.forceSign || flags.spaceSign

    /**
     * Check if any numeric formatting flags are invalid.
     */
    private fun hasInvalidNumericFlags(
        flags: ParsedFlags,
        dimensions: ParsedDimensions,
    ): Boolean =
        flags.zeroPad ||
            dimensions.precision >= 0 ||
            hasSignFlags(flags) ||
            flags.alternateForm

    /**
     * Check if any string formatting flags are invalid.
     */
    private fun hasInvalidStringFlags(flags: ParsedFlags): Boolean = flags.zeroPad || hasSignFlags(flags) || flags.alternateForm

    /**
     * Check if any modifiers are present.
     */
    private fun hasAnyModifiers(
        flags: ParsedFlags,
        dimensions: ParsedDimensions,
    ): Boolean =
        dimensions.width > 0 ||
            dimensions.precision >= 0 ||
            flags.leftAlign ||
            flags.zeroPad ||
            hasSignFlags(flags) ||
            flags.alternateForm

    /**
     * Validate format-specific modifier restrictions.
     */
    private fun validateModifiers(
        formatChar: Char,
        flags: ParsedFlags,
        dimensions: ParsedDimensions,
    ) {
        when (formatChar) {
            'c' -> {
                if (hasInvalidNumericFlags(flags, dimensions)) {
                    throw RuntimeException("invalid conversion")
                }
            }
            's' -> {
                if (hasInvalidStringFlags(flags)) {
                    throw RuntimeException("invalid conversion")
                }
            }
            'q' -> {
                if (hasAnyModifiers(flags, dimensions)) {
                    throw RuntimeException("cannot have modifiers")
                }
            }
            'p' -> {
                if (hasInvalidNumericFlags(flags, dimensions)) {
                    throw RuntimeException("invalid conversion")
                }
            }
            'd', 'i', 'u' -> {
                if (flags.alternateForm) {
                    throw RuntimeException("invalid conversion")
                }
            }
        }
    }

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
