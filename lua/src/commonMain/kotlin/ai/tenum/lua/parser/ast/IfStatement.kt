package ai.tenum.lua.parser.ast

/**
 * If statement
 */
data class IfStatement(
    val condition: Expression,
    val thenBlock: List<Statement>,
    val elseIfBlocks: List<ElseIfBlock>,
    val elseBlock: List<Statement>?,
    override val line: Int,
    val thenLine: Int = line, // Line of 'then' keyword (for debug hooks)
    val endLine: Int = line, // Line of 'end' keyword (for debug hooks)
) : Statement

data class ElseIfBlock(
    val condition: Expression,
    val block: List<Statement>,
    val thenLine: Int = 0, // Line of 'then' keyword (for debug hooks)
)
