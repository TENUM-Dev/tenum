package ai.tenum.lua.parser.ast

/**
 * While loop
 */
data class WhileStatement(
    val condition: Expression,
    val block: List<Statement>,
    override val line: Int,
) : Statement
