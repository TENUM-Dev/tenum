package ai.tenum.lua.lexer

/**
 * Represents a token in the Lua source code
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val line: Int,
    val column: Int,
) {
    override fun toString(): String = "Token($type, '$lexeme', line=$line, col=$column)"
}
