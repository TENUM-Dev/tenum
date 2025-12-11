package ai.tenum.lua.parser.ast

/**
 * Function declaration
 *
 * Supports:
 * - Simple: function name() end
 * - Dot syntax: function t.name() end (table field assignment)
 * - Colon syntax: function t:method() end (method with implicit self)
 */
data class FunctionDeclaration(
    val name: String,
    val parameters: List<String>,
    val hasVararg: Boolean,
    val body: List<Statement>,
    val tablePath: List<String> = emptyList(), // For function a.b.c() - ["a", "b"]
    val isMethod: Boolean = false, // true for function t:method() syntax
    override val line: Int,
    val endLine: Int,
) : Statement
