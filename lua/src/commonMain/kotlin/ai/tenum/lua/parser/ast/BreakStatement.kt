package ai.tenum.lua.parser.ast

/**
 * Break statement
 */
data class BreakStatement(
    override val line: Int,
) : Statement
