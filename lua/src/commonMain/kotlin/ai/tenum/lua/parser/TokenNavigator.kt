package ai.tenum.lua.parser

import ai.tenum.lua.lexer.Token
import ai.tenum.lua.lexer.TokenType

/**
 * Encapsulates token navigation and access logic for parsers.
 * Eliminates code duplication across Parser, ExpressionParser, and StatementParser.
 */
class TokenNavigator(
    private val tokens: List<Token>,
    private val getCurrentIndex: () -> Int,
    private val setCurrentIndex: (Int) -> Unit,
) {
    fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    fun check(type: TokenType): Boolean {
        if (isAtEnd()) return type == TokenType.EOF
        return peek().type == type
    }

    fun checkAny(vararg types: TokenType): Boolean = types.any { check(it) }

    fun checkNext(type: TokenType): Boolean {
        val current = getCurrentIndex()
        if (current + 1 >= tokens.size) return false
        return tokens[current + 1].type == type
    }

    fun advance(): Token {
        val current = getCurrentIndex()
        if (!isAtEnd()) setCurrentIndex(current + 1)
        return previous()
    }

    fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    fun peek(): Token {
        val current = getCurrentIndex()
        return tokens[current]
    }

    fun previous(): Token {
        val current = getCurrentIndex()
        return tokens[current - 1]
    }

    fun consume(
        type: TokenType,
        message: String,
    ): Token {
        if (check(type)) return advance()
        throw ParserException(message, peek())
    }
}
