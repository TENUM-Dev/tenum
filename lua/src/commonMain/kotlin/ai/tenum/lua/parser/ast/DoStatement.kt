package ai.tenum.lua.parser.ast

/**
 * Do-end block
 */
data class DoStatement(
    val block: List<Statement>,
    override val line: Int,
) : Statement
