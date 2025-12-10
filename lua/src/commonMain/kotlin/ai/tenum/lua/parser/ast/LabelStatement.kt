package ai.tenum.lua.parser.ast

/**
 * Label statement (::name::)
 */
data class LabelStatement(
    val name: String,
    override val line: Int,
) : Statement
