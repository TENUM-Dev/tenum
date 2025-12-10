package ai.tenum.lua.parser.ast

/**
 * Vararg expression (...)
 */
data class VarargExpression(
    override val line: Int,
) : Expression
