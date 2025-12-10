package ai.tenum.lua.parser.ast

/**
 * Nil literal
 */
data class NilLiteral(
    override val line: Int,
) : Expression
