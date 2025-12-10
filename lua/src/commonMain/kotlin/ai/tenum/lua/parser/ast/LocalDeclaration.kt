package ai.tenum.lua.parser.ast

/**
 * Local variable declaration: local var = expr
 * Supports Lua 5.4 attributes: <const> and <close>
 */
data class LocalDeclaration(
    val variables: List<LocalVariableInfo>,
    val expressions: List<Expression>,
    override val line: Int,
) : Statement {
    // Backward compatibility: names without attributes
    val names: List<String> get() = variables.map { it.name }
}
