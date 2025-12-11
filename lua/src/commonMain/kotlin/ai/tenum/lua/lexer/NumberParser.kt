package ai.tenum.lua.lexer

import kotlin.math.pow

/**
 * Parser for numeric literals in Lua source code.
 * Handles decimal integers, floats, hexadecimal numbers with optional fractional parts and exponents.
 */
internal class NumberParser {
    companion object {
        private const val MAX_HEX_DIGITS_64BIT = 16
        private const val HEX_RADIX = 16
    }

    /**
     * Parse a decimal number from the source string.
     * Returns the parsed value (Long or Double) or null if parsing fails.
     */
    fun parseDecimal(
        source: String,
        start: Int,
        end: Int,
    ): Any? {
        val text = source.substring(start, end)

        // Distinguish between integer and float literals based on source text
        // Integer: no '.' and no 'e'/'E'
        // Float: has '.' or 'e'/'E'
        val hasDecimalPoint = '.' in text || 'e' in text || 'E' in text

        return if (hasDecimalPoint) {
            // Float literal
            text.toDoubleOrNull()
        } else {
            // Integer literal - try Long first, fall back to Double if overflow
            text.toLongOrNull() ?: text.toDoubleOrNull()
        }
    }

    /**
     * Parse a hexadecimal integer (without fractional part or exponent).
     * Returns Long or Double if the value is too large, or null if parsing fails.
     */
    fun parseHexInteger(hexString: String): Any? {
        // Remove 0x prefix
        val hex = hexString.substringAfter("0x", hexString.substringAfter("0X", ""))
        if (hex.isEmpty()) return null

        try {
            // Lua truncates hex literals longer than 16 digits to lower 64 bits
            val effectiveHex = if (hex.length > MAX_HEX_DIGITS_64BIT) hex.takeLast(MAX_HEX_DIGITS_64BIT) else hex

            // Try parsing as signed long first
            return effectiveHex.toLongOrNull(HEX_RADIX)
                // If that fails, try as unsigned long (for values > Long.MAX_VALUE)
                ?: effectiveHex.toULongOrNull(HEX_RADIX)?.toLong()
                // If still fails, try as double
                ?: parseHexAsDouble(hexString)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse a hexadecimal integer that's too large for 64-bit as a Double.
     * This handles Lua's behavior where integers that overflow become floats.
     */
    fun parseHexAsDouble(hexString: String): Double? {
        try {
            // Remove 0x prefix
            val hex = hexString.substringAfter("0x", hexString.substringAfter("0X", ""))
            if (hex.isEmpty()) return null

            // For hex numbers larger than 64-bit, Lua converts them to float
            // by truncating to 64 bits first, then converting to double.
            // This matches Lua 5.4's behavior where large hex literals are
            // rounded to the nearest representable double.

            // First, try to parse lower 64 bits as Long (signed)
            // We use toLongOrNull with radix 16, which handles values up to 2^63-1
            // For larger values, we use ULong and convert
            val lower64Bits =
                if (hex.length <= MAX_HEX_DIGITS_64BIT) {
                    hex.toLongOrNull(HEX_RADIX) ?: hex.toULongOrNull(HEX_RADIX)?.toLong()
                } else {
                    // Take last 16 hex digits (64 bits)
                    val last16 = hex.takeLast(MAX_HEX_DIGITS_64BIT)
                    last16.toLongOrNull(HEX_RADIX) ?: last16.toULongOrNull(HEX_RADIX)?.toLong()
                }

            if (lower64Bits != null) {
                // Return as Double - Lua stores large integers as floats
                // We use the Long value directly to avoid precision loss
                return lower64Bits.toDouble()
            }

            // Fallback: accumulate as Double (for malformed input)
            var result = 0.0
            for (hexDigit in hex) {
                val digitValue =
                    when (hexDigit.lowercaseChar()) {
                        in '0'..'9' -> hexDigit - '0'
                        in 'a'..'f' -> hexDigit.lowercaseChar() - 'a' + 10
                        else -> return null
                    }
                result = result * HEX_RADIX.toDouble() + digitValue.toDouble()
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse a hexadecimal number with optional fractional part and binary exponent.
     * Format: 0x[mantissa][.[fractional]][p[+-]exponent]
     */
    fun parseHexFloat(hexString: String): Double? {
        try {
            // Remove 0x prefix
            val withoutPrefix = hexString.substring(2)

            // Split by 'p' or 'P' for exponent
            val parts = withoutPrefix.split(Regex("[pP]"))
            val mantissa = parts[0]
            val exponent = if (parts.size > 1) parts[1].toInt() else 0

            // Split mantissa by decimal point
            val mantissaParts = mantissa.split('.')

            // Validate: at most one decimal point (split produces at most 2 parts)
            if (mantissaParts.size > 2) {
                return null // Multiple decimal points: invalid
            }

            // Parse integer part - handle unsigned long values that may overflow signed Long
            val integerPart =
                if (mantissaParts[0].isNotEmpty()) {
                    try {
                        // Try parsing as signed long first
                        mantissaParts[0].toLong(HEX_RADIX).toDouble()
                    } catch (e: NumberFormatException) {
                        // If it overflows, use unsigned interpretation
                        // When there's a fractional part, keep as unsigned float
                        // When there's no fractional part (pure integer), wrap to signed (Lua semantics)
                        val unsignedValue = mantissaParts[0].toULong(HEX_RADIX)
                        if (mantissaParts.size > 1) {
                            // Has fractional part - keep unsigned
                            unsignedValue.toDouble()
                        } else {
                            // Pure integer - wrap to two's complement
                            unsignedValue.toLong().toDouble()
                        }
                    }
                } else {
                    0.0
                }

            val fractionalPart =
                if (mantissaParts.size > 1 && mantissaParts[1].isNotEmpty()) {
                    val fracHex = mantissaParts[1]
                    var fracValue = 0.0
                    for (i in fracHex.indices) {
                        val digitValue = fracHex[i].toString().toInt(HEX_RADIX)
                        fracValue += digitValue / HEX_RADIX.toDouble().pow(i + 1.0)
                    }
                    fracValue
                } else {
                    0.0
                }

            val mantissaValue = integerPart + fractionalPart

            // Apply binary exponent
            return mantissaValue * 2.0.pow(exponent.toDouble())
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Parse a string to a number following Lua 5.4 semantics.
     * Returns the numeric value as Long or Double, or null if conversion fails.
     *
     * Handles:
     * - Whitespace trimming
     * - Hexadecimal literals (0x..., with optional fractional part and exponent)
     * - Decimal numbers (integer and float)
     * - Sign handling
     */
    fun parseStringToNumber(input: String): Any? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Handle hexadecimal literals with exponent (0x1.23p+4)
        // or without exponent (0xABC or 0xABC.DEF)
        if (HexParsingHelpers.isHexLiteral(trimmed)) {
            val (sign, hexPart) = HexParsingHelpers.extractSignAndHexPart(trimmed)

            // Use NumberParser to handle hex floats with exponent (p notation)
            val parsedValue = parseHexFloat(hexPart)
            if (parsedValue != null) {
                return sign * parsedValue
            }

            // Fallback: try parsing as hex integer (no decimal point or exponent)
            val hexMatch = Regex("^0x([0-9a-fA-F]+)$").matchEntire(hexPart)
            if (hexMatch != null) {
                val hexInt = hexMatch.groupValues[1]
                try {
                    // Try direct Long parsing first
                    val intPart = hexInt.toLong(16).toDouble()
                    return sign * intPart
                } catch (e: NumberFormatException) {
                    // Pure integer - apply two's complement wrapping
                    val unsignedValue = hexInt.toULong(16)
                    val intPart = unsignedValue.toLong().toDouble()
                    return sign * intPart
                }
            }

            return null
        }

        try {
            if (!trimmed.contains('.') &&
                !trimmed.contains('e', ignoreCase = true)
            ) {
                return trimmed.toLong()
            }
            return trimmed.toDouble()
        } catch (e: NumberFormatException) {
            return null
        }
    }

    /**
     * Check if a character is a valid hexadecimal digit (0-9, a-f, A-F).
     */
    fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
