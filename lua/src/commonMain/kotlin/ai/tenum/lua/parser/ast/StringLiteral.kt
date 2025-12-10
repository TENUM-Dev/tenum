package ai.tenum.lua.parser.ast

/**
 * String literal
 */
data class StringLiteral(
    val value: String,
    override val line: Int,
) : Expression
