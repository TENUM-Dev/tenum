package ai.tenum.lua.parser.ast

/**
 * Number literal
 * value can be Long (for integers) or Double (for floats)
 */
data class NumberLiteral(
    val value: Any, // Long or Double
    val raw: Any?,
    override val line: Int,
) : Expression
