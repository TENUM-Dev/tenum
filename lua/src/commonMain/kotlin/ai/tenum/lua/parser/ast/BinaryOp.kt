package ai.tenum.lua.parser.ast

import ai.tenum.lua.lexer.Token

/**
 * Binary operation
 */
data class BinaryOp(
    val left: Expression,
    val operator: Token,
    val right: Expression,
    override val line: Int,
) : Expression {
    // For binary operations, errors report the operator line, not operand origins
    // This is different from field access - operators are the "action", not a chain
    // So originLine = line (the operator) is correct
}
