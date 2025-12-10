package ai.tenum.lua.parser.ast

/**
 * Repeat-until loop
 */
data class RepeatStatement(
    val block: List<Statement>,
    val condition: Expression,
    override val line: Int,
) : Statement
