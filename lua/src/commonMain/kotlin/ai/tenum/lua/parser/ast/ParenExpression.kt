package ai.tenum.lua.parser.ast

/**
 * Parenthesized expression
 */
data class ParenExpression(
    val expression: Expression,
    override val line: Int,
) : Expression
