package ai.tenum.lua.parser.ast

/**
 * Expression statement (function call)
 */
data class ExpressionStatement(
    val expression: Expression,
    override val line: Int,
) : Statement
