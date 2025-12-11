package ai.tenum.lua.parser.ast

/**
 * Method call
 */
data class MethodCall(
    val receiver: Expression,
    val method: String,
    val arguments: List<Expression>,
    override val line: Int,
) : Expression
