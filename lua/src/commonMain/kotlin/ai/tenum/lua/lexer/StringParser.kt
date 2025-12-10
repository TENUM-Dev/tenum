package ai.tenum.lua.lexer

/**
 * Parser for string literals in Lua source code.
 * Handles both quoted strings (single/double) and long strings ([[ ]]).
 */
internal class StringParser {
    companion object {
        private const val MAX_BYTE_VALUE = 255
        private const val MAX_UNICODE_CODEPOINT = 0x7FFFFFFF // Lua supports extended UTF-8 up to 2^31-1
        private const val MAX_UNICODE_ESCAPE_DIGITS = 8
        private const val MAX_DECIMAL_ESCAPE_DIGITS = 3
        private const val HEX_RADIX = 16
    }

    /**
     * Parse escape sequences in a quoted string.
     * Returns the processed string or null if there's an error.
     *
     * @param source The source string
     * @param start Starting position after the opening quote
     * @param quote The quote character (' or ")
     * @param sourceName The name of the source file for error reporting
     * @param currentLine The current line number for error reporting
     * @param onAdvance Callback to advance position, returns current char or null if at end
     * @param onNewline Callback when a newline is encountered
     */
    fun parseQuotedString(
        source: String,
        start: Int,
        quote: Char,
        sourceName: String = "<unknown>",
        currentLine: Int = 1,
        onAdvance: () -> Char?,
        onPeek: () -> Char?,
        onNewline: () -> Unit,
    ): ParseResult {
        val builder = StringBuilder()
        var current = start

        while (true) {
            val ch = onPeek() ?: return ParseResult.Error("unfinished string")

            if (ch == quote) {
                onAdvance() // consume closing quote
                return ParseResult.Success(builder.toString())
            }

            // Lua does not allow actual newlines inside quoted strings
            // They must be escaped as \n
            if (ch == '\n' || ch == '\r') {
                return ParseResult.Error("unfinished string")
            }

            if (ch == '\\') {
                onAdvance() // consume backslash
                val escapeResult = parseEscapeSequence(onPeek, onAdvance, onNewline, sourceName, currentLine)
                when (escapeResult) {
                    is EscapeResult.Character -> builder.append(escapeResult.char)
                    is EscapeResult.StringValue -> builder.append(escapeResult.value)
                    is EscapeResult.Skip -> {} // \z - whitespace skip
                    is EscapeResult.Error -> return ParseResult.Error(escapeResult.message)
                }
            } else {
                builder.append(onAdvance() ?: return ParseResult.Error("unfinished string"))
            }
        }
    }

    private fun parseEscapeSequence(
        onPeek: () -> Char?,
        onAdvance: () -> Char?,
        onNewline: () -> Unit,
        sourceName: String = "<unknown>",
        currentLine: Int = 1,
    ): EscapeResult {
        val escapeChar = onPeek() ?: return EscapeResult.Error("unfinished string")

        return when (escapeChar) {
            'a' -> {
                onAdvance()
                EscapeResult.Character('\u0007')
            } // bell
            'b' -> {
                onAdvance()
                EscapeResult.Character('\b')
            } // backspace
            'f' -> {
                onAdvance()
                EscapeResult.Character('\u000C')
            } // form feed
            'n' -> {
                onAdvance()
                EscapeResult.Character('\n')
            } // newline
            'r' -> {
                onAdvance()
                EscapeResult.Character('\r')
            } // carriage return
            't' -> {
                onAdvance()
                EscapeResult.Character('\t')
            } // tab
            'v' -> {
                onAdvance()
                EscapeResult.Character('\u000B')
            } // vertical tab
            '\\' -> {
                onAdvance()
                EscapeResult.Character('\\')
            }
            '"' -> {
                onAdvance()
                EscapeResult.Character('"')
            }
            '\'' -> {
                onAdvance()
                EscapeResult.Character('\'')
            }
            in '0'..'9' -> parseDecimalEscape(escapeChar, onPeek, onAdvance)
            'x' -> parseHexEscape(onPeek, onAdvance)
            'u' -> parseUnicodeEscape(onPeek, onAdvance, sourceName, currentLine)
            'z' -> parseWhitespaceSkip(onPeek, onAdvance, onNewline)
            '\n' -> {
                // Line continuation: backslash followed by actual newline in source
                // This is Lua's line continuation syntax within strings
                onAdvance() // consume the newline
                // Handle \n\r as single line ending
                if (onPeek() == '\r') onAdvance()
                onNewline() // increment line counter
                EscapeResult.Character('\n')
            }
            '\r' -> {
                // Line continuation: backslash followed by carriage return in source
                // Lua 5.4 treats \r as a line ending (for cross-platform compatibility)
                onAdvance() // consume the \r
                // Handle \r\n as single line ending
                if (onPeek() == '\n') onAdvance()
                onNewline() // increment line counter
                EscapeResult.Character('\n')
            }
            else -> {
                // Invalid escape sequence - advance past it to include in error token
                onAdvance()
                EscapeResult.Error("invalid escape sequence")
            }
        }
    }

    private fun parseDecimalEscape(
        firstDigit: Char,
        onPeek: () -> Char?,
        onAdvance: () -> Char?,
    ): EscapeResult {
        onAdvance() // consume first digit
        var decValue = firstDigit.toString().toInt()
        var digitCount = 1

        while (digitCount < MAX_DECIMAL_ESCAPE_DIGITS) {
            val ch = onPeek() ?: break
            if (ch !in '0'..'9') break

            decValue = decValue * 10 + ch.toString().toInt()
            onAdvance()
            digitCount++
        }

        return if (decValue > MAX_BYTE_VALUE) {
            // Advance past closing quote if present to include it in error token
            val next = onPeek()
            if (next == '"' || next == '\'') {
                onAdvance()
            }
            EscapeResult.Error("decimal escape too large")
        } else {
            EscapeResult.Character(decValue.toChar())
        }
    }

    private fun parseHexEscape(
        onPeek: () -> Char?,
        onAdvance: () -> Char?,
    ): EscapeResult {
        onAdvance() // consume 'x'

        val firstDigit = onPeek()
        if (firstDigit == null || !isHexDigit(firstDigit)) {
            // Advance past the invalid character so it appears in error token
            if (firstDigit != null) {
                onAdvance()
            }
            return EscapeResult.Error("hexadecimal digit expected")
        }

        var hexValue = firstDigit.toString().toInt(HEX_RADIX)
        onAdvance()

        val secondDigit = onPeek()
        if (secondDigit == null || !isHexDigit(secondDigit)) {
            // Advance past the invalid character so it appears in error token
            if (secondDigit != null) {
                onAdvance()
            }
            return EscapeResult.Error("hexadecimal digit expected")
        }

        hexValue = hexValue * HEX_RADIX + secondDigit.toString().toInt(HEX_RADIX)
        onAdvance()

        return EscapeResult.Character(hexValue.toChar())
    }

    private fun parseUnicodeEscape(
        onPeek: () -> Char?,
        onAdvance: () -> Char?,
        sourceName: String = "<unknown>",
        currentLine: Int = 1,
    ): EscapeResult {
        onAdvance() // consume 'u'

        if (onPeek() != '{') {
            // Advance past the next character (including quotes) to include in error token
            if (onPeek() != null) {
                onAdvance()
            }
            return EscapeResult.Error("missing '{'")
        }
        onAdvance() // consume '{'

        val hexDigits = StringBuilder()
        // Read all hex digits (don't stop at MAX_UNICODE_ESCAPE_DIGITS)
        // because we need to validate the value, not truncate it
        while (true) {
            val ch = onPeek() ?: break
            if (ch == '}') break
            if (!isHexDigit(ch)) {
                // Advance past non-hex character (including quotes) to include in error token
                onAdvance()
                // If no hex digits were read, error is "hexadecimal digit expected"
                // Otherwise, error is "missing '}'" (malformed escape with digits)
                val errorMsg =
                    if (hexDigits.isEmpty()) {
                        "hexadecimal digit expected"
                    } else {
                        "missing '}'"
                    }
                return EscapeResult.Error(errorMsg)
            }

            hexDigits.append(ch)
            onAdvance()
        }

        if (onPeek() != '}') {
            // Advance past next character (including quotes) to include in error token
            if (onPeek() != null) {
                onAdvance()
            }
            return EscapeResult.Error("missing '}'")
        }

        // Check value BEFORE consuming '}'
        val codePoint = hexDigits.toString().toIntOrNull(HEX_RADIX)
        if (codePoint == null || codePoint < 0 || codePoint > MAX_UNICODE_CODEPOINT) {
            return EscapeResult.Error("UTF-8 value too large")
        }

        // Valid value - consume '}'
        onAdvance()

        // Encode codepoint as UTF-8 byte sequence
        val utf8Bytes = encodeUtf8(codePoint)
        return if (utf8Bytes.isNotEmpty()) {
            // Convert bytes to a string treating each byte as Latin-1 character
            // In Kotlin, Char U+0000 to U+00FF correspond to bytes 0x00 to 0xFF
            val str = utf8Bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
            EscapeResult.StringValue(str)
        } else {
            EscapeResult.Error("UTF-8 value too large")
        }
    }

    /**
     * Encode a Unicode codepoint as UTF-8 bytes.
     * Supports Lua's extended UTF-8 encoding (up to 6 bytes, codepoints up to 0x7FFFFFFF).
     */
    private fun encodeUtf8(codePoint: Int): ByteArray =
        when {
            codePoint < 0 -> byteArrayOf()
            codePoint <= 0x7F -> {
                // 1-byte sequence: 0xxxxxxx
                byteArrayOf(codePoint.toByte())
            }
            codePoint <= 0x7FF -> {
                // 2-byte sequence: 110xxxxx 10xxxxxx
                byteArrayOf(
                    (0xC0 or (codePoint shr 6)).toByte(),
                    continuationByte(codePoint, 0),
                )
            }
            codePoint <= 0xFFFF -> {
                // 3-byte sequence: 1110xxxx 10xxxxxx 10xxxxxx
                byteArrayOf(
                    (0xE0 or (codePoint shr 12)).toByte(),
                    continuationByte(codePoint, 6),
                    continuationByte(codePoint, 0),
                )
            }
            codePoint <= 0x1FFFFF -> {
                // 4-byte sequence: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                encodeMultiByte(codePoint, 0xF0, 18, 4)
            }
            codePoint <= 0x3FFFFFF -> {
                // 5-byte sequence: 111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                encodeMultiByte(codePoint, 0xF8, 24, 5)
            }
            codePoint <= 0x7FFFFFFF -> {
                // 6-byte sequence: 1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
                encodeMultiByte(codePoint, 0xFC, 30, 6)
            }
            else -> byteArrayOf()
        }

    /**
     * Helper to encode multi-byte UTF-8 sequences (4-6 bytes).
     */
    private fun encodeMultiByte(
        codePoint: Int,
        leadByte: Int,
        firstShift: Int,
        byteCount: Int,
    ): ByteArray {
        val bytes = ByteArray(byteCount)
        bytes[0] = (leadByte or (codePoint shr firstShift)).toByte()
        for (i in 1 until byteCount) {
            bytes[i] = continuationByte(codePoint, firstShift - (i * 6))
        }
        return bytes
    }

    /**
     * Create a UTF-8 continuation byte (10xxxxxx pattern).
     */
    private fun continuationByte(
        codePoint: Int,
        shift: Int,
    ): Byte = (0x80 or ((codePoint shr shift) and 0x3F)).toByte()

    private fun parseWhitespaceSkip(
        onPeek: () -> Char?,
        onAdvance: () -> Char?,
        onNewline: () -> Unit,
    ): EscapeResult {
        onAdvance() // consume 'z'

        while (true) {
            when (onPeek()) {
                ' ', '\t', '\r', '\u000B', '\u000C' -> onAdvance() // space, tab, CR, vertical-tab, form-feed
                '\n' -> {
                    onNewline()
                    onAdvance()
                }
                else -> break
            }
        }

        return EscapeResult.Skip
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    sealed class ParseResult {
        data class Success(
            val value: String,
        ) : ParseResult()

        data class Error(
            val message: String,
        ) : ParseResult()
    }

    private sealed class EscapeResult {
        data class Character(
            val char: Char,
        ) : EscapeResult()

        data class StringValue(
            val value: kotlin.String,
        ) : EscapeResult()

        data object Skip : EscapeResult()

        data class Error(
            val message: kotlin.String,
        ) : EscapeResult()
    }
}
