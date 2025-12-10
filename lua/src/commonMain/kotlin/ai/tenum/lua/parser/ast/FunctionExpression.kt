package ai.tenum.lua.parser.ast

/**
 * Function expression (anonymous function)
 */
data class FunctionExpression(
    val parameters: List<String>,
    val hasVararg: Boolean,
    val body: List<Statement>,
    override val line: Int,
    val endLine: Int,
) : Expression
