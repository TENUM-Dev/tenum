package ai.tenum.lua.parser.ast

/**
 * Assignment statement: var = expr
 */
data class Assignment(
    val variables: List<Expression>,
    val expressions: List<Expression>,
    override val line: Int,
) : Statement
