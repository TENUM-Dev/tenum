package ai.tenum.lua.stdlib.string.formatters

import ai.tenum.lua.runtime.LuaBoolean
import ai.tenum.lua.runtime.LuaCoroutine
import ai.tenum.lua.runtime.LuaFunction
import ai.tenum.lua.runtime.LuaNil
import ai.tenum.lua.runtime.LuaNumber
import ai.tenum.lua.runtime.LuaString
import ai.tenum.lua.runtime.LuaTable
import ai.tenum.lua.runtime.LuaUserdata
import ai.tenum.lua.runtime.LuaValue
import ai.tenum.lua.stdlib.string.FormatSpecifier
import ai.tenum.lua.stdlib.string.ValueFormatter

/**
 * Formats values as quoted Lua literals (%q format).
 *
 * Domain: String formatting
 * Responsibility: Generate safe Lua source code literals for values
 */
class QuotedFormatter : ValueFormatter {
    override fun handles(formatChar: Char): Boolean = formatChar == 'q'

    override fun format(
        value: LuaValue<*>,
        spec: FormatSpecifier,
    ): String =
        when (value) {
            is LuaBoolean -> value.value.toString()
            is LuaNil -> "nil"
            is LuaNumber -> formatNumberForQ(value.toDouble())
            is LuaString -> quoteString(value.value)
            is LuaTable, is LuaFunction, is LuaCoroutine, is LuaUserdata<*> -> {
                throw RuntimeException("value has no literal form")
            }
            else -> throw RuntimeException("value has no literal form")
        }

    /**
     * Format a number for %q (quoted format).
     * Lua 5.4 behavior:
     * - NaN: (0/0)
     * - Infinity: 1e9999
     * - -Infinity: -1e9999
     * - math.mininteger: 0x8000000000000000 (hex, to avoid overflow in parser)
     * - Integers: decimal format
     * - Floats: hex float format (0x1.xxxp+y)
     */
    private fun formatNumberForQ(value: Double): String =
        when {
            value.isNaN() -> "(0/0)"
            value.isInfinite() -> if (value > 0) "1e9999" else "-1e9999"
            value == value.toLong().toDouble() -> {
                // Integer value
                val longValue = value.toLong()
                // Special case: math.mininteger must be in hex
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
     * Format a float as hex float (e.g., 0x1.91eb851eb851fp+1 for 3.14).
     */
    private fun formatHexFloat(value: Double): String {
        if (value == 0.0) {
            // Handle both +0 and -0
            val sign = if (1.0 / value < 0) "-" else ""
            return "${sign}0x0p+0"
        }

        val bits = value.toRawBits()
        val negative = bits and (1L shl 63) != 0L
        val exponent = ((bits shr 52) and 0x7FF).toInt() - 1023
        val mantissa = bits and 0xFFFFFFFFFFFFFL

        val signStr = if (negative) "-" else ""
        val mantissaHex = mantissa.toString(16).padStart(13, '0').trimEnd('0')

        // If mantissa is zero (exact power of 2), omit decimal point
        if (mantissa == 0L) {
            return "${signStr}0x1p${if (exponent >= 0) "+" else ""}$exponent"
        }

        return "${signStr}0x1.${mantissaHex}p${if (exponent >= 0) "+" else ""}$exponent"
    }

    /**
     * Quote a string for safe use in Lua source code (%q format).
     * Escapes special characters and wraps in quotes.
     */
    private fun quoteString(str: String): String {
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
}
