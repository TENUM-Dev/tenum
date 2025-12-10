package ai.tenum.lua.lexer

/**
 * Exception thrown when the lexer encounters a lexical error.
 */
class LexerException(
    message: String,
    val line: Int,
    val column: Int,
) : Exception(message)
