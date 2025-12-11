package ai.tenum.lua.parser.ast

/**
 * Boolean literal
 */
data class BooleanLiteral(
    val value: Boolean,
    override val line: Int,
) : Expression
