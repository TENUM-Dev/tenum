package ai.tenum.lua.parser.ast

/**
 * Table access by field name
 */
data class FieldAccess(
    val table: Expression,
    val field: String,
    override val line: Int,
) : Expression
