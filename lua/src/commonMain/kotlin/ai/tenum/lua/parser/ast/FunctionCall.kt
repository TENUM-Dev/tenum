package ai.tenum.lua.parser.ast

/**
 * Function call
 */
data class FunctionCall(
    val function: Expression,
    val arguments: List<Expression>,
    override val line: Int,
) : Expression
