package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Formats values as integers (%d, %i, %u, %o, %x, %X formats).
 *
 * Domain: String formatting
 * Responsibility: Handle integer format specifiers
 */
class IntegerFormatter : ValueFormatter {
    override fun handles(formatChar: Char): Boolean =
        formatChar in setOf('d', 'i', 'u', 'o', 'x', 'X')

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String {
        val num = (value as? LuaNumber)?.value?.toLong() ?: 0L

        val formatted =
            when (spec.formatChar) {
                'd', 'i' -> formatDecimal(num, spec)
                'u' -> formatUnsigned(num, spec)
                'o' -> formatOctal(num, spec)
                'x' -> formatHex(num, spec, uppercase = false)
                'X' -> formatHex(num, spec, uppercase = true)
                else -> throw IllegalArgumentException("Unsupported format: ${spec.formatChar}")
            }

        return formatted
    }

    private fun formatDecimal(
        num: Long,
        spec: FormatSpecifier,
    ): String {
        // Special case: precision 0 with value 0 produces empty string
        var numStr =
            if (spec.precision == 0 && num == 0L) {
                ""
            } else {
                num.toString()
            }

        // Apply precision: minimum number of digits (zero-pad the number part)
        if (spec.precision != null && spec.precision > 0 && numStr.isNotEmpty()) {
            val isNegative = numStr.startsWith('-')
            val absStr = if (isNegative) numStr.substring(1) else numStr
            if (absStr.length < spec.precision) {
                val zeros = "0".repeat(spec.precision - absStr.length)
                numStr = if (isNegative) "-$zeros$absStr" else "$zeros$absStr"
            }
        }

        // Apply forceSign flag: show + for positive numbers
        if (spec.forceSign && num >= 0 && numStr.isNotEmpty()) {
            numStr = "+$numStr"
        }

        return applyWidth(numStr, spec.width ?: 0, spec.leftJustify, spec.zeroPad)
    }

    private fun formatUnsigned(
        num: Long,
        spec: FormatSpecifier,
    ): String {
        // Convert to unsigned representation
        val unsignedStr = num.toULong().toString()

        return applyPrecisionAndWidth(unsignedStr, spec)
    }

    private fun formatOctal(
        num: Long,
        spec: FormatSpecifier,
    ): String {
        val octalStr = num.toULong().toString(8)

        // Apply alternate form: prefix with 0
        val formatted = if (spec.alternate && num != 0L) "0$octalStr" else octalStr

        return applyPrecisionAndWidth(formatted, spec)
    }

    /**
     * Apply precision (zero-padding to meet minimum digits) and width formatting.
     * Domain: String formatting
     * Responsibility: Apply precision and width to already formatted number strings
     */
    private fun applyPrecisionAndWidth(
        formatted: String,
        spec: FormatSpecifier,
    ): String {
        var result = formatted
        
        // Apply precision: minimum number of digits (zero-pad on the left)
        if (spec.precision != null && spec.precision > 0 && result.length < spec.precision) {
            result = "0".repeat(spec.precision - result.length) + result
        }

        return applyWidth(result, spec.width ?: 0, spec.leftJustify, spec.zeroPad)
    }

    private fun formatHex(
        num: Long,
        spec: FormatSpecifier,
        uppercase: Boolean,
    ): String {
        val hexStr = num.toULong().toString(16).let { if (uppercase) it.uppercase() else it }

        // Apply alternate form: prefix with 0x or 0X
        var formatted =
            if (spec.alternate && num != 0L) {
                if (uppercase) "0X$hexStr" else "0x$hexStr"
            } else {
                hexStr
            }

        // Apply precision
        if (spec.precision != null && spec.precision > 0) {
            val prefixLen = if (spec.alternate && num != 0L) 2 else 0
            val numDigits = formatted.length - prefixLen
            if (numDigits < spec.precision) {
                val zeros = "0".repeat(spec.precision - numDigits)
                formatted =
                    if (prefixLen > 0) {
                        formatted.substring(0, prefixLen) + zeros + formatted.substring(prefixLen)
                    } else {
                        zeros + formatted
                    }
            }
        }

        return applyWidth(formatted, spec.width ?: 0, spec.leftJustify, spec.zeroPad)
    }

    private fun applyWidth(
        str: String,
        width: Int,
        leftAlign: Boolean,
        zeroPad: Boolean,
    ): String {
        if (width <= 0 || str.length >= width) return str

        val paddingChar = if (zeroPad && !leftAlign) '0' else ' '
        val padding = paddingChar.toString().repeat(width - str.length)

        // For zero-padding, insert zeros after sign
        if (zeroPad && !leftAlign && (str.startsWith('-') || str.startsWith('+'))) {
            return str.substring(0, 1) + padding + str.substring(1)
        }

        return if (leftAlign) str + padding else padding + str
    }
}
