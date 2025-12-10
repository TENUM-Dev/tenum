package ai.tenum.lua.vm.errorhandling

/**
 * Represents a single frame in the Lua call stack
 */
data class LuaStackFrame(
    val functionName: String?,
    val source: String?,
    val line: Int?,
)
