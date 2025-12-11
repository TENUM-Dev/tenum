package ai.tenum.lua.parser.ast

/**
 * Variable reference
 */
data class Variable(
    val name: String,
    override val line: Int,
) : Expression
