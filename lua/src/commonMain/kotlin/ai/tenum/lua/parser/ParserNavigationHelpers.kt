package ai.tenum.lua.parser

import ai.tenum.lua.lexer.TokenType

/**
 * Provides common navigation delegate methods for parsers.
 * Eliminates duplication of trivial TokenNavigator forwarding.
 */
interface ParserNavigationHelpers {
    val nav: TokenNavigator

    fun match(vararg types: TokenType) = nav.match(*types)

    fun check(type: TokenType) = nav.check(type)

    fun checkNext(type: TokenType) = nav.checkNext(type)

    fun checkAny(vararg types: TokenType) = nav.checkAny(*types)

    fun advance() = nav.advance()

    fun isAtEnd() = nav.isAtEnd()

    fun peek() = nav.peek()

    fun previous() = nav.previous()

    fun consume(
        type: TokenType,
        message: String,
    ) = nav.consume(type, message)

    fun error(message: String): Nothing = throw ParserException(message, peek())
}
