package ai.tenum.lua.stdlib.string

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaUserdata
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
                        if (zeroPad || precision >= 0 || forceSign || spaceSign || alternateForm) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                    's' -> {
                        // %s: accepts width, '-', and precision
                        // No zero padding, no +/space/#
                        if (zeroPad || forceSign || spaceSign || alternateForm) {
                            throw RuntimeException("invalid conversion")
                        }
                    }
                    'q' -> {
                        // %q: no modifiers at all
                        if (width > 0 || precision >= 0 || leftAlign || zeroPad || forceSign || spaceSign || alternateForm) {
                            throw RuntimeException("cannot have modifiers")
                        }
                    }
                    'p' -> {
                        // %p: accepts width and '-' flag
                        // No zero padding, no precision, no +/space/#
                        if (zeroPad || precision >= 0 || forceSign || spaceSign || alternateForm) {
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
                    's' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handleString(values[valueIndex], width, precision, leftAlign, valueIndex, valueToString)
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'd', 'i' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handleInteger(values[valueIndex], width, precision, leftAlign, zeroPad, forceSign)
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'p' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handlePointer(values[valueIndex], width, leftAlign)
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'e', 'E' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement =
                            handleExponential(
                                values[valueIndex],
                                width,
                                precision,
                                leftAlign,
                                zeroPad,
                                forceSign,
                                spaceSign,
                                formatChar == 'E',
                            )
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'f', 'g', 'G' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement =
                            handleFloat(
                                values[valueIndex],
                                width,
                                precision,
                                leftAlign,
                                zeroPad,
                                forceSign,
                                spaceSign,
                                alternateForm,
                                formatChar,
                            )
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'q' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val formatted = handleQuoted(values[valueIndex], valueIndex)
                        result = result.substring(0, i) + formatted + result.substring(j + 1)
                        i += formatted.length
                        valueIndex++
                    }
                    'a', 'A' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement =
                            handleHexFloat(
                                values[valueIndex],
                                width,
                                precision,
                                leftAlign,
                                zeroPad,
                                forceSign,
                                formatChar == 'A',
                            )
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'x', 'X' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement =
                            handleHex(
                                values[valueIndex],
                                width,
                                precision,
                                leftAlign,
                                zeroPad,
                                alternateForm,
                                formatChar == 'X',
                            )
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'u' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handleUnsigned(values[valueIndex], width, precision, leftAlign, zeroPad)
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'o' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handleOctal(values[valueIndex], width, precision, leftAlign, zeroPad, alternateForm)
                        result = result.substring(0, i) + replacement + result.substring(j + 1)
                        i += replacement.length
                        valueIndex++
                    }
                    'c' -> {
                        if (valueIndex >= values.size) {
                            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (no value)")
                        }
                        val replacement = handleChar(values[valueIndex], width, leftAlign)
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

    // ============================================================================
    // Format Handler Methods
    // ============================================================================

    /**
     * Handle %% escape sequence
     */
    private fun handleEscapePercent(): FormatReplacement = FormatReplacement("%", 1)

    /**
     * Handle %s (string) format
     */
    private fun handleString(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        valueIndex: Int,
        valueToString: (LuaValue<*>) -> String,
    ): String {
        var str = valueToString(value)
        // If width is specified and string contains null bytes, error
        if (width > 0 && str.contains('\u0000')) {
            throw RuntimeException("bad argument #${valueIndex + 2} to 'string.format' (string contains zeros)")
        }
        // Apply precision: truncate string to at most precision characters
        if (precision >= 0 && str.length > precision) {
            str = str.substring(0, precision)
        }
        return applyWidth(str, width, leftAlign)
    }

    /**
     * Handle %d and %i (integer) format
     */
    private fun handleInteger(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        forceSign: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.value?.toLong() ?: 0

        // Special case: precision 0 with value 0 produces empty string
        var numStr =
            if (precision == 0 && num == 0L) {
                ""
            } else {
                num.toString()
            }

        // Apply precision: minimum number of digits (zero-pad the number part)
        if (precision > 0 && numStr.isNotEmpty()) {
            val isNegative = numStr.startsWith('-')
            val absStr = if (isNegative) numStr.substring(1) else numStr
            if (absStr.length < precision) {
                val zeros = "0".repeat(precision - absStr.length)
                numStr = if (isNegative) "-$zeros$absStr" else "$zeros$absStr"
            }
        }

        // Apply forceSign flag: show + for positive numbers
        if (forceSign && num >= 0 && numStr.isNotEmpty()) {
            numStr = "+$numStr"
        }
        return applyWidth(numStr, width, leftAlign, zeroPad)
    }

    /**
     * Handle %p (pointer) format
     */
    private fun handlePointer(
        value: LuaValue<*>,
        width: Int,
        leftAlign: Boolean,
    ): String {
        val pointerStr = formatPointer(value)
        return applyWidth(pointerStr, width, leftAlign)
    }

    /**
     * Handle %e and %E (exponential) format
     */
    private fun handleExponential(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        forceSign: Boolean,
        spaceSign: Boolean,
        uppercase: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0
        val formatted = formatExponential(num, precision, uppercase)
        return applySignAndWidth(formatted, num, width, leftAlign, zeroPad, forceSign, spaceSign)
    }

    /**
     * Handle %f, %g, and %G (float) format
     */
    private fun handleFloat(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        forceSign: Boolean,
        spaceSign: Boolean,
        alternateForm: Boolean,
        formatChar: Char,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0
        val formatted =
            if (formatChar == 'g' || formatChar == 'G') {
                // %g/%G: choose between %e and %f based on exponent
                // Default precision for %g is 6 if not specified
                val prec = if (precision >= 0) precision else 6
                val shouldUseExp = shouldUseExponentialForG(num, prec)

                if (shouldUseExp) {
                    // Use exponential format
                    val exp = formatExponential(num, prec - 1, formatChar == 'G')
                    if (!alternateForm) exp.trimEnd('0').trimEnd('.') else exp
                } else {
                    // Use decimal format
                    val raw = formatFloatWithPrecision(num, prec, alternateForm)
                    if (!alternateForm) raw.trimEnd('0').trimEnd('.') else raw
                }
            } else if (precision >= 0) {
                formatFloatWithPrecision(num, precision, alternateForm)
            } else {
                num.toString()
            }

        return applySignAndWidth(formatted, num, width, leftAlign, zeroPad, forceSign, spaceSign)
    }

    /**
     * Handle %q (quoted) format
     */
    private fun handleQuoted(
        value: LuaValue<*>,
        valueIndex: Int,
    ): String =
        when (value) {
            is LuaBoolean -> value.value.toString()
            is LuaNil -> "nil"
            is LuaNumber -> formatNumberForQ(value.toDouble())
            is LuaString -> quoteString(value.value)
            is LuaTable, is LuaFunction, is LuaCoroutine, is LuaUserdata<*> -> {
                throw RuntimeException(
                    "bad argument #${valueIndex + 2} to 'string.format' (value has no literal form)",
                )
            }
            else -> {
                val str = ArgumentHelpers.coerceToString(value)
                quoteString(str)
            }
        }

    /**
     * Handle %a and %A (hex float) format
     */
    private fun handleHexFloat(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        forceSign: Boolean,
        uppercase: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.toDouble() ?: 0.0
        val hexFloat = formatHexFloat(num, precision, forceSign)
        val formatted =
            if (uppercase) {
                hexFloat.replace("0x", "0X").replace("p", "P").uppercase()
            } else {
                hexFloat
            }
        return applyWidth(formatted, width, leftAlign, zeroPad)
    }

    /**
     * Handle %x and %X (hexadecimal) format
     */
    private fun handleHex(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        alternateForm: Boolean,
        uppercase: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.value?.toLong() ?: 0
        // Special case: precision 0 with value 0 produces empty string
        var hexStr =
            if (precision == 0 && num == 0L) {
                ""
            } else if (uppercase) {
                num.toULong().toString(16).uppercase()
            } else {
                num.toULong().toString(16).lowercase()
            }

        // Apply precision: minimum number of digits
        if (precision > 0 && hexStr.isNotEmpty()) {
            if (hexStr.length < precision) {
                val zeros = "0".repeat(precision - hexStr.length)
                hexStr = "$zeros$hexStr"
            }
        }

        // Apply alternate form: prefix with 0x or 0X
        if (alternateForm && hexStr.isNotEmpty()) {
            hexStr = if (uppercase) "0X$hexStr" else "0x$hexStr"
        }
        return applyWidth(hexStr, width, leftAlign, zeroPad)
    }

    /**
     * Handle %u (unsigned) format
     */
    private fun handleUnsigned(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.value?.toLong() ?: 0
        // Special case: precision 0 with value 0 produces empty string
        var unsignedStr =
            if (precision == 0 && num == 0L) {
                ""
            } else {
                num.toULong().toString()
            }

        // Apply precision: minimum number of digits
        if (precision > 0 && unsignedStr.isNotEmpty()) {
            if (unsignedStr.length < precision) {
                val zeros = "0".repeat(precision - unsignedStr.length)
                unsignedStr = "$zeros$unsignedStr"
            }
        }

        return applyWidth(unsignedStr, width, leftAlign, zeroPad)
    }

    /**
     * Handle %o (octal) format
     */
    private fun handleOctal(
        value: LuaValue<*>,
        width: Int,
        precision: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        alternateForm: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.value?.toLong() ?: 0
        // Special case: precision 0 with value 0 produces empty string
        var octalStr =
            if (precision == 0 && num == 0L) {
                ""
            } else {
                num.toULong().toString(8)
            }

        // Apply precision: minimum number of digits
        if (precision > 0 && octalStr.isNotEmpty()) {
            if (octalStr.length < precision) {
                val zeros = "0".repeat(precision - octalStr.length)
                octalStr = "$zeros$octalStr"
            }
        }

        // Apply alternate form: prefix with 0
        if (alternateForm && octalStr.isNotEmpty()) {
            octalStr = "0$octalStr"
        }
        return applyWidth(octalStr, width, leftAlign, zeroPad)
    }

    /**
     * Handle %c (character) format
     */
    private fun handleChar(
        value: LuaValue<*>,
        width: Int,
        leftAlign: Boolean,
    ): String {
        val num = (value as? LuaNumber)?.value?.toInt() ?: 0
        val char = num.toChar().toString()
        return applyWidth(char, width, leftAlign)
    }

    /**
     * Data class for format replacement result
     */
    private data class FormatReplacement(
        val replacement: String,
        val offset: Int,
    )

    // ============================================================================
    // Helper Methods
    // ============================================================================

    /**
     * Quote a string for safe use in Lua source code (%q format)
     * Escapes special characters and wraps in quotes
     * Lua 5.4 behavior:
     * - Escape: " \ \n \r \t \0 and control characters (< 32)
     * - Keep literal: printable ASCII (32-126) and bytes 127-255 (UTF-8)
     */
    fun quoteString(str: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (ch in str) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u0000' -> sb.append("\\0")
                else -> {
                    if (ch < ' ') {
                        // Control characters (0-31): use decimal escape
                        sb.append("\\").append(ch.code)
                    } else {
                        // Printable ASCII and high bytes (UTF-8): keep literal
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Apply sign flags (+ or space) and width to a formatted number string
     */
    fun applySignAndWidth(
        formatted: String,
        num: Double,
        width: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
        forceSign: Boolean,
        spaceSign: Boolean,
    ): String {
        var result = formatted

        // Apply sign flags: + takes precedence over space
        if (forceSign && num >= 0.0 && !result.startsWith('+')) {
            result = "+$result"
        } else if (!forceSign && spaceSign && num >= 0.0 && !result.startsWith('+') && !result.startsWith(' ')) {
            result = " $result"
        }

        return applyWidth(result, width, leftAlign, zeroPad)
    }

    /**
     * Apply width and alignment to a string
     */
    fun applyWidth(
        str: String,
        width: Int,
        leftAlign: Boolean,
        zeroPad: Boolean = false,
    ): String {
        if (width <= 0 || str.length >= width) return str

        val padChar = if (zeroPad && !leftAlign) '0' else ' '
        val padding = padChar.toString().repeat(width - str.length)

        return if (leftAlign) {
            str + padding // Left align: string first, then padding
        } else if (zeroPad && (str.startsWith('-') || str.startsWith('+') || str.startsWith(' '))) {
            // For numbers with sign, put sign first, then zero padding, then digits
            str[0] + padding + str.substring(1)
        } else {
            padding + str // Right align (default): padding first, then string
        }
    }

    /**
     * Determine if %g/%G should use exponential format
     * Per C standard: use exponential if exponent < -4 or exponent >= precision
     */
    fun shouldUseExponentialForG(
        value: Double,
        precision: Int,
    ): Boolean {
        if (value == 0.0 || value.isNaN() || value.isInfinite()) return false

        val absValue = abs(value)
        val exponent = floor(log10(absValue)).toInt()

        return exponent < -4 || exponent >= precision
    }

    /**
     * Format a number in exponential notation (e.g., 1.234567e+02)
     * @param value the number to format
     * @param precision number of digits after decimal point (default 6)
     * @param uppercase whether to use 'E' instead of 'e'
     * @return formatted string
     */
    fun formatExponential(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String {
        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) return if (uppercase) (if (value > 0) "INF" else "-INF") else (if (value > 0) "inf" else "-inf")
        if (value == 0.0) {
            val expChar = if (uppercase) 'E' else 'e'
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
                round(mantissa)
                    .toInt()
                    .toString()
            } else {
                formatFloatWithPrecision(mantissa, prec)
            }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expChar = if (uppercase) 'E' else 'e'
        val expSign = if (exponent >= 0) "+" else ""
        val expStr =
            abs(exponent)
                .toString()
                .padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
    }

    /**
     * Format exponential for %g style (with trailing zero trimming).
     * This is used internally by formatGStyle.
     */
    private fun formatExponentialForG(
        value: Double,
        precision: Int,
        uppercase: Boolean,
    ): String {
        // Handle special values
        if (value.isNaN()) return if (uppercase) "NAN" else "nan"
        if (value.isInfinite()) return if (uppercase) (if (value > 0) "INF" else "-INF") else (if (value > 0) "inf" else "-inf")
        if (value == 0.0) {
            val expChar = if (uppercase) 'E' else 'e'
            return "0$expChar+00"
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
                // Format and trim trailing zeros from mantissa
                formatFloatWithPrecision(mantissa, prec).trimEnd('0').trimEnd('.')
            }

        // Format exponent with at least 2 digits (Lua 5.4 behavior)
        val expChar = if (uppercase) 'E' else 'e'
        val expSign = if (exponent >= 0) "+" else ""
        val expStr =
            abs(exponent)
                .toString()
                .padStart(2, '0')

        val sign = if (isNegative) "-" else ""
        return "$sign$mantissaStr$expChar$expSign$expStr"
    }

    /**
     * Format a number using %g style formatting with specified precision.
     * This matches Lua's "%.14g" default number formatting.
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
     * Format a number for %q (quoted format)
     * Lua 5.4 behavior:
     * - NaN: (0/0)
     * - Infinity: 1e9999
     * -Infinity: -1e9999
     * - math.mininteger: 0x8000000000000000 (hex, to avoid overflow in parser)
     * - Integers: decimal format
     * - Floats: hex float format (0x1.xxxp+y)
     */
    fun formatNumberForQ(value: Double): String =
        when {
            value.isNaN() -> "(0/0)"
            value.isInfinite() -> if (value > 0) "1e9999" else "-1e9999"
            value == value.toLong().toDouble() -> {
                // Integer value
                val longValue = value.toLong()
                // Special case: math.mininteger (-9223372036854775808) must be in hex
                // because -9223372036854775808 would be parsed as -(9223372036854775808)
                // and 9223372036854775808 overflows to float
                if (longValue == Long.MIN_VALUE) {
                    "0x8000000000000000"
                } else {
                    longValue.toString()
                }
            }
            else -> {
                // Float value - use hex float format
                formatHexFloat(value)
            }
        }

    /**
     * Format a float as hex float (e.g., 0x1.91eb851eb851fp+1 for 3.14)
     * This matches Lua 5.4's behavior for %q on float numbers
     * Format: [sign]0x1.mantissap[sign]exponent
     * The mantissa is in hex, representing the fractional part after the implicit 1.
     */
    fun formatHexFloat(
        value: Double,
        precision: Int = -1,
        forceSign: Boolean = false,
    ): String {
        if (value == 0.0) {
            // Special case for zero (handle both +0 and -0)
            val sign =
                if (1.0 / value < 0) {
                    "-"
                } else if (forceSign) {
                    "+"
                } else {
                    ""
                }
            return if (precision >= 0) {
                // With precision, always show decimal point
                "${sign}0x0." + "0".repeat(precision) + "p+0"
            } else {
                "${sign}0x0p+0"
            }
        }

        val bits = value.toRawBits()
        val negative = bits and (1L shl 63) != 0L
        val exponent = ((bits shr 52) and 0x7FF).toInt() - 1023
        // Mask for 52 bits: 2^52 - 1 = 0xFFFFFFFFFFFFF (13 hex F's)
        val mantissa = bits and 0xFFFFFFFFFFFFFL

        val signStr =
            if (negative) {
                "-"
            } else if (forceSign) {
                "+"
            } else {
                ""
            }

        // The mantissa has 52 bits, which is 13 hex digits
        // We need to format it as exactly 13 hex digits to preserve all bits
        val mantissaHex = mantissa.toString(16).padStart(13, '0')

        // Apply precision if specified
        val formattedMantissa =
            if (precision >= 0) {
                // Precision specifies number of hex digits after decimal point
                mantissaHex.take(precision).padEnd(precision, '0')
            } else {
                // No precision: remove trailing zeros from mantissa for cleaner output
                mantissaHex.trimEnd('0')
            }

        // If mantissa is zero (exact power of 2) and no precision specified,
        // Lua omits the decimal point: 0x1p+0 not 0x1.0p+0
        if (mantissa == 0L && precision < 0) {
            return "${signStr}0x1p${if (exponent >= 0) "+" else ""}$exponent"
        }

        // Include decimal point if we have mantissa digits or precision is explicitly set
        val decimalPart = if (formattedMantissa.isEmpty() && precision < 0) "" else ".$formattedMantissa"

        return "${signStr}0x1${decimalPart}p${if (exponent >= 0) "+" else ""}$exponent"
    }

    /**
     * Format pointer according to Lua rules:
     * - Primitives (numbers, booleans, nil) → "(null)"
     * - Reference types (tables, functions, coroutines) → unique address
     * - Strings use identityId for proper internalization semantics
     */
    fun formatPointer(value: LuaValue<*>): String =
        when (value) {
            is LuaNumber, is LuaBoolean, is LuaNil -> "(null)"
            else -> {
                // For other reference types, use hashCode as unique identifier
                val address = value.hashCode().toString(16)
                "0x$address"
            }
        }

    /**
     * Format float with precision, always using '.' as decimal separator
     * Uses BigDecimal to handle very large numbers and high precision formatting
     */
    fun formatFloatWithPrecision(
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
