package ai.tenum.lua.parser.ast

/**
 * For-in loop (generic)
 */
data class ForInStatement(
    val variables: List<String>,
    val expressions: List<Expression>,
    val block: List<Statement>,
    override val line: Int,
) : Statement
