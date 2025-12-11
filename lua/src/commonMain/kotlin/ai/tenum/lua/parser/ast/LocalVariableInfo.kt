package ai.tenum.lua.parser.ast

/**
 * Information about a local variable including attributes
 * In Lua 5.4, a variable can have both <const> and <close> attributes
 */
data class LocalVariableInfo(
    val name: String,
    val isConst: Boolean = false,
    val isClose: Boolean = false,
)
