package ai.tenum.lua.parser.ast

/**
 * Goto statement
 */
data class GotoStatement(
    val label: String,
    override val line: Int,
) : Statement
