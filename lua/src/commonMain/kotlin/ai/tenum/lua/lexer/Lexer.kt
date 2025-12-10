package ai.tenum.lua.lexer

/**
 * Lexer for Lua source code
 * Converts source code into a stream of tokens
 */
class Lexer(
    private val source: String,
    private val sourceName: String = "<unknown>",
) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1

    private val numberParser = NumberParser()
    private val stringParser = StringParser()

    companion object {
        private val keywords =
            mapOf(
                "and" to TokenType.AND,
                "break" to TokenType.BREAK,
                "do" to TokenType.DO,
                "else" to TokenType.ELSE,
                "elseif" to TokenType.ELSEIF,
                "end" to TokenType.END,
                "false" to TokenType.FALSE,
                "for" to TokenType.FOR,
                "function" to TokenType.FUNCTION,
                "goto" to TokenType.GOTO,
                "if" to TokenType.IF,
                "in" to TokenType.IN,
                "local" to TokenType.LOCAL,
                "nil" to TokenType.NIL,
                "not" to TokenType.NOT,
                "or" to TokenType.OR,
                "repeat" to TokenType.REPEAT,
                "return" to TokenType.RETURN,
                "then" to TokenType.THEN,
                "true" to TokenType.TRUE,
                "until" to TokenType.UNTIL,
                "while" to TokenType.WHILE,
            )
    }

    /**
     * Scan all tokens from the source code
     */
    fun scanTokens(): List<Token> {
        // Note: Shebang handling (lines starting with #) is done at the file loading level,
        // not here in the lexer. This allows load() to correctly reject invalid syntax like "#=1"
        // while still supporting shebangs in actual Lua files.

        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line, column))
        return tokens
    }

    /**
     * Scan a single token from the current position.
     * Advances the current position and adds the token to the tokens list.
     */
    private fun scanToken() {
        val startColumn = column
        val startLine = line
        val c = advance()

        when (c) {
            '(' -> addToken(TokenType.LEFT_PAREN, startColumn)
            ')' -> addToken(TokenType.RIGHT_PAREN, startColumn)
            '{' -> addToken(TokenType.LEFT_BRACE, startColumn)
            '}' -> addToken(TokenType.RIGHT_BRACE, startColumn)
            '[' -> {
                // Check for long string [[ or [=[ syntax
                if (peek() == '[' || peek() == '=') {
                    longString(startColumn)
                } else {
                    addToken(TokenType.LEFT_BRACKET, startColumn)
                }
            }
            ']' -> addToken(TokenType.RIGHT_BRACKET, startColumn)
            ';' -> addToken(TokenType.SEMICOLON, startColumn)
            ',' -> addToken(TokenType.COMMA, startColumn)
            '+' -> addToken(TokenType.PLUS, startColumn)
            '-' -> {
                // Check for comments
                if (match('-')) {
                    // Check for multi-line comment --[[ or --[=[
                    if (peek() == '[') {
                        val savedPos = current
                        advance() // consume '['
                        var level = 0
                        while (peek() == '=') {
                            level++
                            advance()
                        }
                        if (peek() == '[') {
                            advance() // consume second '['
                            // Multi-line comment
                            multiLineComment(level)
                        } else {
                            // Not a multi-line comment, reset and treat as single-line
                            current = savedPos
                            while (peek() != '\n' && peek() != '\r' && !isAtEnd()) advance()
                        }
                    } else {
                        // Single-line comment until end of line
                        while (peek() != '\n' && peek() != '\r' && !isAtEnd()) advance()
                    }
                } else {
                    addToken(TokenType.MINUS, startColumn)
                }
            }
            '*' -> addToken(TokenType.MULTIPLY, startColumn)
            '/' -> {
                // Check for floor division //
                if (match('/')) {
                    addToken(TokenType.FLOOR_DIVIDE, startColumn)
                } else {
                    addToken(TokenType.DIVIDE, startColumn)
                }
            }
            '%' -> addToken(TokenType.MODULO, startColumn)
            '^' -> addToken(TokenType.POWER, startColumn)
            '#' -> addToken(TokenType.HASH, startColumn)
            ':' -> {
                if (match(':')) {
                    addToken(TokenType.DOUBLE_COLON, startColumn)
                } else {
                    addToken(TokenType.COLON, startColumn)
                }
            }
            '&' -> addToken(TokenType.BITWISE_AND, startColumn)
            '|' -> addToken(TokenType.BITWISE_OR, startColumn)
            '~' -> {
                if (match('=')) {
                    addToken(TokenType.NOT_EQUAL, startColumn)
                } else {
                    addToken(TokenType.BITWISE_XOR, startColumn)
                }
            }
            '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQUAL, startColumn)
                } else {
                    addToken(TokenType.ASSIGN, startColumn)
                }
            }
            '<' -> {
                if (match('=')) {
                    addToken(TokenType.LESS_EQUAL, startColumn)
                } else if (match('<')) {
                    addToken(TokenType.SHIFT_LEFT, startColumn)
                } else {
                    addToken(TokenType.LESS, startColumn)
                }
            }
            '>' -> {
                if (match('=')) {
                    addToken(TokenType.GREATER_EQUAL, startColumn)
                } else if (match('>')) {
                    addToken(TokenType.SHIFT_RIGHT, startColumn)
                } else {
                    addToken(TokenType.GREATER, startColumn)
                }
            }
            '.' -> {
                if (match('.')) {
                    if (match('.')) {
                        addToken(TokenType.VARARG, startColumn)
                    } else {
                        addToken(TokenType.CONCAT, startColumn)
                    }
                } else if (isDigit(peek())) {
                    number(startColumn)
                } else {
                    addToken(TokenType.DOT, startColumn)
                }
            }
            ' ', '\t', '\u000B', '\u000C' -> {
                // Ignore whitespace (space, tab, vertical tab, form feed)
            }
            '\n' -> {
                line++
                column = 1
                // Handle \n\r as a single line ending
                if (peek() == '\r') advance()
            }
            '\r' -> {
                line++
                column = 1
                // Handle \r\n as a single line ending
                if (peek() == '\n') advance()
            }
            '"', '\'' -> string(c, startColumn)
            else -> {
                when {
                    isDigit(c) -> number(startColumn)
                    isAlpha(c) -> identifier(startColumn)
                    else -> addToken(TokenType.ERROR, startColumn)
                }
            }
        }
    }

    /**
     * Scan an identifier or keyword token.
     * Identifiers start with a letter or underscore, followed by letters, digits, or underscores.
     */
    private fun identifier(startColumn: Int) {
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        addToken(type, startColumn)
    }

    /**
     * Scan a numeric literal (decimal or hexadecimal).
     * Supports integers, floats, and exponential notation.
     */
    private fun number(startColumn: Int) {
        // Check for hexadecimal number
        if (current < source.length &&
            source[start] == '0' &&
            current + 1 < source.length &&
            (source[current] == 'x' || source[current] == 'X')
        ) {
            advance() // consume 'x' or 'X'
            hexNumber(startColumn)
            return
        }

        // Decimal number
        while (isDigit(peek())) advance()

        // Look for decimal part
        if (peek() == '.' && isDigit(peekNext())) {
            advance() // consume the '.'
            while (isDigit(peek())) advance()
        } else if (peek() == '.') {
            // Check if next is exponent marker or end of number
            val next = peekNext()
            if (next == 'e' || next == 'E' || !isAlpha(next) && next != '.') {
                // Trailing decimal point like "5." or "5.e2"
                advance() // consume the '.'
            }
        }

        // Look for exponent
        if (peek() == 'e' || peek() == 'E') {
            if (!parseExponent(peek(), startColumn)) return
        }

        // Check for malformed number: if next character is alphabetic without whitespace,
        // it's a malformed number like "1print" or "1.5x"
        if (isAlpha(peek())) {
            // Consume until we hit whitespace or non-alphanumeric
            while (isAlphaNumeric(peek())) advance()
            addToken(TokenType.ERROR, startColumn)
            return
        }

        val value = numberParser.parseDecimal(source, start, current)
        if (value == null) {
            addToken(TokenType.ERROR, startColumn)
        } else {
            addToken(TokenType.NUMBER, startColumn, value)
        }
    }

    /**
     * Parse a hexadecimal number (0x...) with optional fractional part and binary exponent.
     * Supports forms like: 0x1A, 0x1.8p2, 0xA.Bp-3
     */
    private fun hexNumber(startColumn: Int) {
        // Parse hexadecimal number
        var hasDigits = false
        var hasFractionalPart = false
        var hasExponent = false

        // Integer part
        while (numberParser.isHexDigit(peek())) {
            advance()
            hasDigits = true
        }

        // Fractional part
        if (peek() == '.') {
            hasFractionalPart = true
            advance()
            while (numberParser.isHexDigit(peek())) {
                advance()
                hasDigits = true
            }
        }

        if (!hasDigits) {
            addToken(TokenType.ERROR, startColumn)
            return
        }

        // Binary exponent (p or P)
        if (peek() == 'p' || peek() == 'P') {
            hasExponent = true
            if (!parseExponent(peek(), startColumn)) return
        }

        // Check for malformed hex number: if next character is alphabetic,
        // it's malformed like "0xg" or "0x1.2g"
        if (isAlpha(peek())) {
            // Consume until we hit whitespace or non-alphanumeric
            while (isAlphaNumeric(peek())) advance()
            addToken(TokenType.ERROR, startColumn)
            return
        }

        val hexString = source.substring(start, current)

        // If no fractional part and no exponent, parse as integer
        val value =
            if (!hasFractionalPart && !hasExponent) {
                numberParser.parseHexInteger(hexString)
            } else {
                numberParser.parseHexFloat(hexString)
            }

        if (value == null) {
            addToken(TokenType.ERROR, startColumn)
        } else {
            addToken(TokenType.NUMBER, startColumn, value)
        }
    }

    /**
     * Parse a quoted string literal (single or double quoted).
     * Handles escape sequences including \n, \t, \xHH, \u{XXX}, \ddd, \z.
     */
    private fun string(
        quote: Char,
        startColumn: Int,
    ) {
        val startPos = current
        val result =
            stringParser.parseQuotedString(
                source = source,
                start = current,
                quote = quote,
                sourceName = sourceName,
                currentLine = line,
                onAdvance = {
                    if (isAtEnd()) null else advance()
                },
                onPeek = {
                    if (isAtEnd()) null else peek()
                },
                onNewline = {
                    line++
                    column = 1
                },
            )

        when (result) {
            is StringParser.ParseResult.Success -> {
                addToken(TokenType.STRING, startColumn, result.value)
            }
            is StringParser.ParseResult.Error -> {
                // For "unfinished string" errors that hit EOF, report "near <eof>"
                // For other errors (invalid escapes, etc.), show the problematic token content
                val errorNear =
                    if (result.message == "unfinished string" && isAtEnd()) {
                        "<eof>"
                    } else {
                        val errorToken = source.substring(startPos - 1, current.coerceAtMost(source.length))
                        "'$errorToken'"
                    }
                throw LexerException(
                    message = "${result.message} near $errorNear",
                    line = line,
                    column = startColumn,
                )
            }
        }
    }

    /**
     * Parse a long string literal with [[ ]], [=[ ]=], [==[ ]==], etc.
     * The number of = signs in the opening bracket must match the closing bracket.
     * Skips the first newline after the opening bracket (Lua spec).
     */
    private fun longString(startColumn: Int) {
        // Long string syntax: [[ ... ]] or [=[ ... ]=] or [==[ ... ]==], etc.
        // Count the number of '=' signs in the opening bracket
        var equalCount = 0
        while (peek() == '=') {
            equalCount++
            advance()
        }

        // Must have '[' after the '=' signs
        if (peek() != '[') {
            addToken(TokenType.ERROR, startColumn)
            return
        }
        advance() // consume the second '['

        // Skip the first newline if present (Lua spec)
        // Handle all line ending forms: \n, \r, \r\n, \n\r
        if (peek() == '\n') {
            advance()
            if (peek() == '\r') advance() // consume \r after \n (handles \n\r)
            line++
            column = 1
        } else if (peek() == '\r') {
            advance()
            if (peek() == '\n') advance() // consume \n after \r (handles \r\n)
            line++
            column = 1
        }

        val builder = StringBuilder()

        // Read until we find the matching closing bracket
        while (!isAtEnd()) {
            if (peek() == ']') {
                if (tryMatchClosingBracket(equalCount)) {
                    // Found the closing bracket!
                    addToken(TokenType.STRING, startColumn, builder.toString())
                    return
                } else {
                    // Not the closing bracket, add the character
                    builder.append(advance())
                }
            } else {
                val ch = peek()
                if (ch == '\n') {
                    advance()
                    if (peek() == '\r') advance() // consume \r after \n (handles \n\r)
                    line++
                    column = 1
                    builder.append('\n')
                } else if (ch == '\r') {
                    advance()
                    if (peek() == '\n') advance() // consume \n after \r (handles \r\n)
                    line++
                    column = 1
                    builder.append('\n')
                } else {
                    builder.append(advance())
                }
            }
        }

        // Unterminated long string - report as lexer error with <eof>
        throw LexerException(
            message = "unfinished long string near <eof>",
            line = line,
            column = startColumn,
        )
    }

    /**
     * Parse a multi-line comment --[[ ]], --[=[ ]=], --[==[ ]==], etc.
     * Reads until the matching closing bracket ]=...=].
     * Unterminated comments are silently accepted (Lua behavior).
     */
    private fun multiLineComment(equalCount: Int) {
        // Read until we find the matching closing bracket ]=...=]
        while (!isAtEnd()) {
            if (peek() == ']') {
                if (tryMatchClosingBracket(equalCount)) {
                    // Found the closing bracket! Comment is complete, just return
                    return
                } else {
                    // Not the closing bracket, continue
                    advance()
                }
            } else {
                val ch = peek()
                if (ch == '\n') {
                    line++
                    column = 1
                    advance()
                } else if (ch == '\r') {
                    advance()
                    if (peek() == '\n') advance()
                    line++
                    column = 1
                } else {
                    advance()
                }
            }
        }
        // Unterminated comment - just silently end (Lua behavior)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        column++
        return true
    }

    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current]
    }

    private fun peekNext(): Char {
        if (current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }

    private fun advance(): Char {
        column++
        return source[current++]
    }

    /**
     * Helper to check if we've found the closing bracket ]=...=] with the right number of '=' signs.
     * Returns true if found, false otherwise. Restores position if not found.
     */
    private fun tryMatchClosingBracket(equalCount: Int): Boolean {
        val savedCurrent = current
        val savedColumn = column
        val savedLine = line

        advance() // consume ']'

        // Count '=' signs
        var closingEqualCount = 0
        while (peek() == '=' && closingEqualCount < equalCount) {
            closingEqualCount++
            advance()
        }

        // Check if we have the right number of '=' and a final ']'
        if (closingEqualCount == equalCount && peek() == ']') {
            advance() // consume final ']'
            return true
        } else {
            // Not the closing bracket, restore position
            current = savedCurrent
            column = savedColumn
            line = savedLine
            return false
        }
    }

    private fun addToken(
        type: TokenType,
        startColumn: Int,
        literal: Any? = null,
    ) {
        val text = source.substring(start, current)
        val startLine =
            if (type == TokenType.STRING && literal != null && (literal as String).contains('\n')) {
                // For multi-line strings, we need to track the starting line properly
                line - (literal as String).count { it == '\n' }
            } else {
                line
            }
        tokens.add(Token(type, text, literal, startLine, startColumn))
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun isAlpha(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)

    /**
     * Parse an exponent (e/E/p/P) in a numeric literal.
     * Returns true if successful, false if invalid exponent.
     */
    private fun parseExponent(
        exponentChar: Char,
        startColumn: Int,
    ): Boolean {
        advance() // consume exponent character
        if (peek() == '+' || peek() == '-') advance() // consume sign
        if (!isDigit(peek())) {
            // Invalid exponent - consume remaining alphanumeric characters
            // to include them in the malformed token (e.g., "0xep-p" or "1e+a")
            while (isAlphaNumeric(peek()) || peek() == '+' || peek() == '-') advance()
            addToken(TokenType.ERROR, startColumn)
            return false
        }
        while (isDigit(peek())) advance()
        return true
    }
}
