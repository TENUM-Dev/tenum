package ai.tenum.lua.parser.ast

/**
 * For loop (numeric)
 */
data class ForStatement(
    val variable: String,
    val start: Expression,
    val end: Expression,
    val step: Expression?,
    val block: List<Statement>,
    val isConst: Boolean = false,
    val isClose: Boolean = false,
    override val line: Int,
) : Statement
