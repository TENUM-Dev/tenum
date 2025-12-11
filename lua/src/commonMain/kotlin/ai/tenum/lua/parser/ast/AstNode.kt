package ai.tenum.lua.parser.ast

/**
 * Base class for all AST nodes
 * All nodes track the line number in the source code where they appear.
 */
sealed interface AstNode {
    /**
     * The line where this AST node's token/operator appears in source code.
     * For example:
     * - BinaryOp: line of the operator (+, -, etc.)
     * - FieldAccess: line of the dot (.)
     * - FunctionCall: line of the opening paren (
     * - Identifier: line of the identifier itself
     */
    val line: Int
}
