package ai.tenum.lua.parser.ast

/**
 * Table access by index
 */
data class IndexAccess(
    val table: Expression,
    val index: Expression,
    override val line: Int,
) : Expression
