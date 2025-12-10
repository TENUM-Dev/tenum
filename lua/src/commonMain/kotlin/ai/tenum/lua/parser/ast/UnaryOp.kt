package ai.tenum.lua.parser.ast

import ai.tenum.lua.lexer.Token

/**
 * Unary operation
 */
data class UnaryOp(
    val operator: Token,
    val operand: Expression,
    override val line: Int,
) : Expression
