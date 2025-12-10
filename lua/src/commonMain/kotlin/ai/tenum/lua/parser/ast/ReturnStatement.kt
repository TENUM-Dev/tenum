package ai.tenum.lua.parser.ast

/**
 * Return statement
 */
data class ReturnStatement(
    val expressions: List<Expression>,
    override val line: Int,
) : Statement
