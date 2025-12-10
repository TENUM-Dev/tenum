package ai.tenum.lua.parser.ast

/**
 * Local function declaration
 */
data class LocalFunctionDeclaration(
    val name: String,
    val parameters: List<String>,
    val hasVararg: Boolean,
    val body: List<Statement>,
    override val line: Int,
    val endLine: Int,
) : Statement
