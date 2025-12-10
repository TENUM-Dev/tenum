package ai.tenum.lua.parser.ast

/**
 * Represents a Lua chunk (a sequence of statements)
 */
data class Chunk(
    val statements: List<Statement>,
    override val line: Int = 1,
) : AstNode
